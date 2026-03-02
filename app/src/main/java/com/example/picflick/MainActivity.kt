package com.example.picflick

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.picflick.ui.theme.PicFlickTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val auth = FirebaseAuth.getInstance()
        setContent {
            PicFlickTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFD7ECFF)
                ) {
                    MainScreen(auth)
                }
            }
        }
    }
}

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val followers: List<String> = emptyList(),
    val following: List<String> = emptyList()
)

data class Flick(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val timestamp: Long = 0,
    val likes: List<String> = emptyList()
)

data class ChatSession(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTimestamp: Long = 0
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0
)

@Composable
fun MainScreen(auth: FirebaseAuth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var currentScreen by remember { mutableStateOf("home") }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            db.collection("users").document(currentUser!!.uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    userProfile = snapshot.toObject(UserProfile::class.java)
                }
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize().padding(paddingValues), color = Color(0xFFD7ECFF)) {
            if (currentUser == null) {
                LoginScreen { user ->
                    saveUserToFirestore(user, db)
                    currentUser = user
                }
            } else if (userProfile != null) {
                Crossfade(targetState = currentScreen, label = "screen") { screen ->
                    when (screen) {
                        "home" -> HomeScreen(
                            userProfile = userProfile!!,
                            refreshTrigger = refreshTrigger,
                            onNavigate = { currentScreen = it },
                            onSignOut = { auth.signOut(); currentUser = null }
                        )
                        "profile" -> ProfileScreen(userProfile!!, currentUser!!, { currentScreen = "home" }, { auth.signOut(); currentUser = null })
                        "my_photos" -> MyPhotosScreen(currentUser!!, { currentScreen = "home" })
                        "friends" -> FriendsScreen(userProfile!!, { currentScreen = "home" })
                        "chats" -> ChatsScreen(userProfile!!, { currentScreen = "home" })
                        "find_friends" -> FindFriendsScreen(userProfile!!, { currentScreen = "home" })
                        "about" -> AboutScreen { currentScreen = "home" }
                        "contact" -> ContactScreen { currentScreen = "home" }
                        "notifications" -> NotificationsScreen(userProfile!!) { currentScreen = "home" }
                        else -> HomeScreen(userProfile!!, refreshTrigger, { currentScreen = it }, { auth.signOut(); currentUser = null })
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (FirebaseUser) -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    var isLoading by remember { mutableStateOf(false) }

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("830332240569-pko9aotmkb9qn86ao98ml20drrihe4l4.apps.googleusercontent.com")
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
                isLoading = true
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { authTask ->
                        isLoading = false
                        if (authTask.isSuccessful) {
                            auth.currentUser?.let { onLoginSuccess(it) }
                        }
                    }
            } catch (e: ApiException) {
                isLoading = false
                Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFF2196F3), CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("PF", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text("Welcome to PicFlick", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Share photos with friends", fontSize = 16.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { launcher.launch(googleSignInClient.signInIntent) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(Icons.Default.AccountCircle, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Google")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userProfile: UserProfile,
    refreshTrigger: Int,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    var flicks by remember { mutableStateOf<List<Flick>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun loadFlicks() {
        db.collection("flicks").orderBy("timestamp", Query.Direction.DESCENDING).limit(50).get()
            .addOnSuccessListener { flicks = it.toObjects(Flick::class.java); loading = false }
            .addOnFailureListener { loading = false }
    }

    LaunchedEffect(refreshTrigger) { loadFlicks() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PicFlick") },
                navigationIcon = {
                    IconButton(onClick = { onNavigate("profile") }) {
                        AsyncImage(
                            model = userProfile.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                            error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate("notifications") }) {
                        Icon(Icons.Default.Notifications, null)
                    }
                    IconButton(onClick = { onNavigate("chats") }) {
                        Icon(Icons.AutoMirrored.Filled.Send, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Camera */ }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (flicks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No photos yet. Upload one!")
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                    items(flicks) { flick ->
                        Card(modifier = Modifier.padding(4.dp).clickable { }) {
                            Column {
                                AsyncImage(
                                    model = flick.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    contentScale = ContentScale.Crop
                                )
                                Row(modifier = Modifier.padding(8.dp)) {
                                    IconButton(onClick = {
                                        db.collection("flicks").document(flick.id)
                                            .update("likes", if (flick.likes.contains(userProfile.uid)) FieldValue.arrayRemove(userProfile.uid) else FieldValue.arrayUnion(userProfile.uid))
                                        loadFlicks()
                                    }) {
                                        Icon(
                                            if (flick.likes.contains(userProfile.uid)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            null,
                                            tint = if (flick.likes.contains(userProfile.uid)) Color.Red else Color.Gray
                                        )
                                    }
                                    Text("${flick.likes.size}", modifier = Modifier.align(Alignment.CenterVertically))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(userProfile: UserProfile, user: FirebaseUser, onBack: () -> Unit, onSignOut: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            AsyncImage(
                model = userProfile.photoUrl,
                contentDescription = null,
                modifier = Modifier.size(120.dp).clip(CircleShape).border(3.dp, Color.Blue, CircleShape),
                error = painterResource(id = android.R.drawable.ic_menu_myplaces)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(userProfile.displayName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(userProfile.email, fontSize = 14.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("0", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Photos")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(userProfile.followers.size.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Followers")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(userProfile.following.size.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Following")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = { }) {
                Text("Edit Profile")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(onClick = onSignOut) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPhotosScreen(user: FirebaseUser, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var photos by remember { mutableStateOf<List<Flick>>(emptyList()) }

    LaunchedEffect(user.uid) {
        db.collection("flicks").whereEqualTo("userId", user.uid).get()
            .addOnSuccessListener { photos = it.toObjects(Flick::class.java) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (photos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No photos yet")
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                    items(photos) { photo ->
                        AsyncImage(
                            model = photo.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.padding(2.dp).aspectRatio(1f),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(userProfile: UserProfile, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (userProfile.following.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No friends yet. Find some!")
                }
            } else {
                Text("Following ${userProfile.following.size} users")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(userProfile: UserProfile, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Messages coming soon!")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindFriendsScreen(userProfile: UserProfile, onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val db = FirebaseFirestore.getInstance()
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }

    fun search() {
        if (searchQuery.isBlank()) return
        db.collection("users").orderBy("displayName").startAt(searchQuery).endAt(searchQuery + "\uf8ff").limit(20).get()
            .addOnSuccessListener { results = it.toObjects(UserProfile::class.java).filter { it.uid != userProfile.uid } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Friends") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; search() },
                label = { Text("Search users...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(results) { user ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = user.photoUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                                error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName, fontWeight = FontWeight.Bold)
                                Text("${user.followers.size} followers", fontSize = 12.sp, color = Color.Gray)
                            }
                            Button(onClick = {
                                db.collection("users").document(userProfile.uid).update("following", FieldValue.arrayUnion(user.uid))
                                db.collection("users").document(user.uid).update("followers", FieldValue.arrayUnion(userProfile.uid))
                            }) {
                                Text(if (userProfile.following.contains(user.uid)) "Following" else "Follow")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(userProfile: UserProfile, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Notifications, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("No notifications yet", fontSize = 18.sp, color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier.size(100.dp).background(Color(0xFF2196F3), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("PF", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("PicFlick", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Version 1.0", fontSize = 16.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "A photo sharing app for friends and family.",
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                minLines = 3
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_SUBJECT, "PicFlick: $subject")
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    context.startActivity(Intent.createChooser(intent, "Send email"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Email, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Feedback")
            }
        }
    }
}

// Helper functions
fun saveUserToFirestore(user: FirebaseUser, db: FirebaseFirestore) {
    val updates = mapOf(
        "uid" to user.uid,
        "email" to (user.email ?: ""),
        "displayName" to (user.displayName ?: ""),
        "photoUrl" to (user.photoUrl?.toString() ?: "")
    )
    db.collection("users").document(user.uid).set(updates, SetOptions.merge())
}

fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        diff < 604800000 -> "${diff / 86400000}d ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
