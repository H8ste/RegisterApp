package com.example.registerapp

import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import com.example.registerapp.ui.theme.RegisterAppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {

    private var parsedNfcTag by mutableStateOf<JSONObject?>(null)
    private var isLookingForNFCTag by mutableStateOf<Boolean>(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp(
                onNfcSearchBegun = { isLookingForNFCTag = true; },
                onNfcSearchEnded = { isLookingForNFCTag = false; parsedNfcTag = null; },
                parsedNfcTag
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = NfcAdapter.getDefaultAdapter(this)

        if (adapter != null) {
            val intent = Intent(applicationContext, this.javaClass)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            val pendingIntent =
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
            val techList = arrayOf(arrayOf("android.nfc.tech.Ndef"))
            val intentFilter: Array<IntentFilter> =
                arrayOf(IntentFilter("android.nfc.action.NDEF_DISCOVERED", "text/plain"))
            adapter.enableForegroundDispatch(this, pendingIntent, intentFilter, techList)
        }
    }

    override fun onPause() {
        super.onPause()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        adapter?.disableForegroundDispatch(this)
    }


    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)


        Log.d("MainActivity", "looking for tag?: $isLookingForNFCTag")
        if (isLookingForNFCTag) {
            if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
                val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                parsedNfcTag = parseNdefMessages(rawMsgs)

                if (parsedNfcTag != null) isLookingForNFCTag = false
            }
        }
    }

    private fun parseNdefMessages(rawMsgs: Array<out Parcelable>?): JSONObject? {
        rawMsgs?.let { msgs ->
            if (msgs.size == 1) {
                val ndefMessage = msgs[0] as? NdefMessage
                ndefMessage?.let { msg ->
                    val ndefRecords = msg.records
                    if (ndefRecords.size == 1) {
                        val payload = ndefRecords[0].payload
                        // Check if payload represents a JSON string
                        val payloadString = String(payload)
                        return try {
                            JSONObject(payloadString)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        }
        return null
    }
}


@Composable
fun ReadNFCOverlay(
    onOverlayClose: () -> Unit,
    onEnableEmail: () -> Unit,
    onDisableEmail: () -> Unit,
    parsedNfcTag: JSONObject?,
    isCheckIn: Boolean,
    isCheckOut: Boolean
) {
    if (parsedNfcTag != null) {
        val isValidTag = parsedNfcTag.has("userId") && parsedNfcTag.has("admin")
        if (isValidTag) {
            val userId = parsedNfcTag.getString("userId")
            val isAdmin = parsedNfcTag.getBoolean("admin")

            val sharedPreferences =
                LocalContext.current.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)

            if (isCheckIn) {
                if (isAdmin) {
                    onEnableEmail()
                }
                // check if user is already checked in
                if (!isUserRegistered(sharedPreferences, userId)) {
                    onUserRegistered(sharedPreferences, userId)
                    showToast(LocalContext.current, "$userId successfully checked in!")
                } else {
                    showToast(LocalContext.current, "$userId is already checked in!")
                }
            } else if (isCheckOut) {
                onDisableEmail()
                // check if user is not yet checked in
                if (isUserRegistered(sharedPreferences, userId)) {
                    val timeRegistered = onUserDetached(sharedPreferences, userId)

                    // subtract time registered by current time
                    appendStoredRegistration(
                        sharedPreferences,
                        RegistrationObject(userId, timeRegistered, System.currentTimeMillis())
                    )

                    showToast(
                        LocalContext.current,
                        "$userId successfully checked out! ${formatDate(timeRegistered)}"
                    )
                } else {
                    showToast(LocalContext.current, "$userId is not checked in!")
                }
            } else {
                showToast(LocalContext.current, "Wierd!")
                onDisableEmail()
            }
        }

        if (isValidTag) {
            onOverlayClose()
        }
        // do some more here
        Log.d("MainActivity", "a nfc tag was parsed!")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .wrapContentSize()
                .background(Color.White)
                .padding(0.dp)
                .sizeIn(minHeight = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(141, 195, 185)
            )
        ) {
            Box(
                modifier = Modifier
                    //.fillMaxSize()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(45.dp, 15.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Content centered horizontally and vertically
                    CircularProgressIndicator(
                        color = Color.White,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Text(
                        text = "Scan kort",
                        color = Color.Black,
                        modifier = Modifier.padding(top = 16.dp), // Add padding between text and CircularProgressIndicator
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 'X' button in the top right corner without taking up space
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(0.dp),
                ) {
                    IconButton(
                        onClick = { onOverlayClose() },
                        modifier = Modifier
                            .size(48.dp) // Set the size of the 'X' button
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Close"
                        )
                    }
                }
            }
        }
    }
}

fun formatDate(milliseconds: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val date = Date(milliseconds)
    return sdf.format(date)
}

@Composable
fun SendEmailButton(onButtonClicked: () -> Unit) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            Log.d("MainActivity", "Sending email!")
            sendStoredRegistrations(context)
            onButtonClicked()
        },
        modifier = Modifier
            .size(48.dp) // Set the size of the 'X' button
    ) {
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = ""
        )
    }
}

@Suppress("SpellCheckingInspection")
@Composable
fun MyApp(onNfcSearchBegun: () -> Unit, onNfcSearchEnded: () -> Unit, jsonTag: JSONObject?) {
    var waitForReadNFCTag by remember { mutableStateOf(false) }
    var isCheckInState by remember { mutableStateOf(false) }
    var isCheckOutState by remember { mutableStateOf(false) }
    var showEmailIconState by remember { mutableStateOf(false) }

    RegisterAppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(0.dp),
                ) {
                    if (showEmailIconState) {
                        SendEmailButton(onButtonClicked = { showEmailIconState = false; })
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ArrowButton(text = "Tjek ind",
                            icon = Icons.Default.ArrowForward,
                            color = Color.Green,
                            onClick = {
                                waitForReadNFCTag = true; isCheckInState = true; isCheckOutState =
                                false; onNfcSearchBegun()
                            })
                        Spacer(modifier = Modifier.height(16.dp))
                        ArrowButton(text = "Tjek ud",
                            icon = Icons.Default.ArrowBack,
                            color = Color.Red,
                            onClick = {
                                waitForReadNFCTag = true; isCheckInState = false; isCheckOutState =
                                true; onNfcSearchBegun()
                            })
                    }
                }
                if (waitForReadNFCTag) {
                    // Overlay layer
                    Box(

                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ReadNFCOverlay(
                            onOverlayClose = {
                                waitForReadNFCTag = false; isCheckOutState = false; isCheckInState =
                                false; onNfcSearchEnded()
                            },
                            onEnableEmail = { showEmailIconState = true; },
                            onDisableEmail = { showEmailIconState = false; },
                            parsedNfcTag = jsonTag,
                            isCheckIn = isCheckInState, isCheckOut = isCheckOutState
                        )
                    }
                }
            }
        }
    }
}

fun getListOfMembersRegistered(sharedPreferences: SharedPreferences): Array<DataObject> {
    val value = sharedPreferences.getString("registeredMembers", "[]")

    // parse value as json Array
    val listOfMembersJSONArray = try {
        JSONArray(value)
    } catch (e: Exception) {
        null
    }

    return jsonArrayToDataObjectArray(listOfMembersJSONArray)
}

fun jsonArrayToRegistrationObjectArray(jsonArray: JSONArray?): Array<RegistrationObject> {
    val registrationObjectArray = mutableListOf<RegistrationObject>()
    if (jsonArray != null && jsonArray.length() > 0) {
        for (i in 0 until jsonArray.length()) {
            val element = jsonArray.getString(i)

            val elementAsJsonObject = try {
                JSONObject(element)
            } catch (e: Exception) {
                null
            }


            Log.d("MainActivity", "jsonElement: ${elementAsJsonObject.toString()}")

            if (elementAsJsonObject != null &&
                elementAsJsonObject.has("userId") &&
                elementAsJsonObject.has("clockInTimestamp") &&
                elementAsJsonObject.has("clockOutTimestamp")
            ) {
                registrationObjectArray.add(
                    RegistrationObject(
                        elementAsJsonObject.getString("userId"),
                        elementAsJsonObject.getLong("clockInTimestamp"),
                        elementAsJsonObject.getLong("clockOutTimestamp")
                    )
                )
            } else {
                Log.d("MainActivity", "failed to add : ${elementAsJsonObject?.toString()}")
            }
        }
    }
    return registrationObjectArray.toTypedArray()
}

fun jsonArrayToDataObjectArray(jsonArray: JSONArray?): Array<DataObject> {
    val dataObjectArray = mutableListOf<DataObject>()
    if (jsonArray != null && jsonArray.length() > 0) {
        for (i in 0 until jsonArray.length()) {
            val element = jsonArray.getString(i)

            val elementAsJsonObject = try {
                JSONObject(element)
            } catch (e: Exception) {
                null
            }


            Log.d("MainActivity", "jsonElement: ${elementAsJsonObject.toString()}")

            if (elementAsJsonObject != null &&
                elementAsJsonObject.has("userId") &&
                elementAsJsonObject.has("timestamp")
            ) {
                dataObjectArray.add(
                    DataObject(
                        elementAsJsonObject.getString("userId"),
                        elementAsJsonObject.getLong("timestamp")
                    )
                )
            } else {
                Log.d("MainActivity", "failed to add : ${elementAsJsonObject.toString()}")
            }
        }
    }
    return dataObjectArray.toTypedArray()
}

fun getStoredRegistrations(sharedPreferences: SharedPreferences): Array<RegistrationObject> {
    val value = sharedPreferences.getString("storedRegistrations", "[]")

    // parse value as json Array
    val listOfMembersJSONArray = try {
        JSONArray(value)
    } catch (e: Exception) {
        null
    }

    return jsonArrayToRegistrationObjectArray(listOfMembersJSONArray)
}

fun appendStoredRegistration(
    sharedPreferences: SharedPreferences,
    registrationObject: RegistrationObject
) {
    val editor = sharedPreferences.edit()
    val registrations = getStoredRegistrations(sharedPreferences)
    val updatedList = registrations.toMutableList()

    // Add the new userId to the list
    updatedList.add(registrationObject)


    Log.d("MainActivity", "attempting to save finished registration: ${registrationObject.userId}")

    val listOfMembersJSONArray = arrayToJsonArray(updatedList.toTypedArray())

    Log.d("MainActivity", "?: $listOfMembersJSONArray")
    editor.putString("storedRegistrations", listOfMembersJSONArray.toString())
    editor.apply()
}

fun clearStoredRegistrations(sharedPreferences: SharedPreferences) {
    val editor = sharedPreferences.edit()
    editor.putString("storedRegistrations", null)
    editor.apply()
}

fun isUserRegistered(sharedPreferences: SharedPreferences, userId: String): Boolean {
    val listOfMembers = getListOfMembersRegistered(sharedPreferences)

    return listOfMembers.any { it.userId == userId }
}

data class DataObject(val userId: String, val timestamp: Long, val admin: Boolean = false)

data class RegistrationObject(
    val userId: String,
    val clockInTimestamp: Long,
    val clockOutTimestamp: Long
)

fun updateRegisteredUsers(sharedPreferences: SharedPreferences, users: Array<DataObject>) {
    val editor = sharedPreferences.edit()
    //editor.putString("registeredMembers", null);
    //editor.apply()

    for (user in users) {
        Log.d("MainActivity", "attempting to save: ${user.userId}")
    }

    val listOfMembersJSONArray = arrayToJsonArray(users)

    Log.d("MainActivity", "?: $listOfMembersJSONArray")
    editor.putString("registeredMembers", listOfMembersJSONArray.toString())
    editor.apply()
}


fun arrayToJsonArray(dataArray: Array<DataObject>): JSONArray {
    val jsonArray = JSONArray()
    for (dataObject in dataArray) {
        val jsonObject = JSONObject()
        jsonObject.put("userId", dataObject.userId)
        jsonObject.put("timestamp", dataObject.timestamp)
        jsonArray.put(jsonObject)
    }
    return jsonArray
}

fun arrayToJsonArray(dataArray: Array<RegistrationObject>): JSONArray {
    val jsonArray = JSONArray()
    for (registrationObject in dataArray) {
        val jsonObject = JSONObject()
        jsonObject.put("userId", registrationObject.userId)
        jsonObject.put("clockInTimestamp", registrationObject.clockInTimestamp)
        jsonObject.put("clockOutTimestamp", registrationObject.clockOutTimestamp)
        jsonArray.put(jsonObject)
    }
    return jsonArray
}

fun onUserRegistered(sharedPreferences: SharedPreferences, userId: String) {
    // append the user
    val listOfMembers = getListOfMembersRegistered(sharedPreferences)
    val updatedList = listOfMembers.toMutableList()

    // Add the new userId to the list
    updatedList.add(DataObject(userId, System.currentTimeMillis()))

    updateRegisteredUsers(sharedPreferences, updatedList.toTypedArray())
}

fun onUserDetached(sharedPreferences: SharedPreferences, userId: String): Long {
    // pop the user
    var listOfMembers = getListOfMembersRegistered(sharedPreferences)

    val poppedUser = listOfMembers.find { it.userId == userId }
    listOfMembers = listOfMembers.filter { it.userId != userId }.toTypedArray()

    updateRegisteredUsers(sharedPreferences, listOfMembers)

    if (poppedUser != null) {
        return poppedUser.timestamp
    }

    return 0
}

enum class ButtonState { Pressed, Idle }

fun Modifier.bounceClick() = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    val scale by animateFloatAsState(if (buttonState == ButtonState.Pressed) 0.70f else 1f)

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { }
        )
        .pointerInput(buttonState) {
            awaitPointerEventScope {
                buttonState = if (buttonState == ButtonState.Pressed) {
                    waitForUpOrCancellation()
                    ButtonState.Idle
                } else {
                    awaitFirstDown(false)
                    ButtonState.Pressed
                }
            }
        }
}

@Composable
fun ArrowButton(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(120.dp)
            .bounceClick(), // Animate content size change,
        colors = ButtonDefaults.buttonColors(color),

        ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, color = Color.White)
        }
    }
}

fun showToast(context: Context, message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(context, message, duration).show()
}

fun sendStoredRegistrations(context: Context) {
    // get user registrations
    val sharedPreferences = context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
    val existingRegistrations = getStoredRegistrations(sharedPreferences)

    if (existingRegistrations.isNotEmpty()) {
        // send email with registrations (if any)
        val subject = "Tidsregisteringer " + getCurrentDayInLocaleFormat(Locale.getDefault())
        var body = "Navn,Ankomst,Afgang\r\n"
        for (registration in existingRegistrations) {
            body += "${registration.userId},${formatDate(registration.clockInTimestamp)},${
                formatDate(
                    registration.clockOutTimestamp
                )
            } \r\n"
        }

        val result = sendEmailToLoggedInUser(subject, body, context)

        if (result) {
            // successfully logged existing registration; delete registrations (if any)
            clearStoredRegistrations(sharedPreferences)
        }
    }
}


fun getCurrentDayInLocaleFormat(locale: Locale): String {
    val dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, locale)
    return dateFormat.format(Date())
}

fun sendEmailToLoggedInUser(subject: String, body: String, context: Context): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    // Get the email address of the current logged-in user
    val accountManager = AccountManager.get(context)
    val loggedInUserEmail: String? = accountManager.accounts.firstOrNull()?.name

    if (!loggedInUserEmail.isNullOrEmpty()) {
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(loggedInUserEmail))
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        startActivity(context, Intent.createChooser(intent, "Send Email"), null)
        return true
    } else {
        // Handle no email app available
        showToast(context, "Failed to launch email app!")
        return false
    }
}
