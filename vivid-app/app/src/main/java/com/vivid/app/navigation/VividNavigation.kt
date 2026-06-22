package com.vivid.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.domain.repository.ChatRepository
import com.vivid.app.presentation.auth.AuthScreen
import com.vivid.app.presentation.create.CameraScreen
import com.vivid.app.presentation.create.CreatePostScreen
import com.vivid.app.presentation.feed.FeedScreen
import com.vivid.app.presentation.messages.ChatListScreen
import com.vivid.app.presentation.messages.ChatScreen
import com.vivid.app.presentation.profile.BlockedUsersScreen
import com.vivid.app.presentation.profile.CloseFriendsScreen
import com.vivid.app.presentation.profile.EditProfileScreen
import com.vivid.app.presentation.profile.FollowRequestsScreen
import com.vivid.app.presentation.profile.ProfileScreen
import com.vivid.app.presentation.profile.SettingsScreen
import com.vivid.app.presentation.reels.ReelsScreen
import com.vivid.app.presentation.search.SearchScreen
import com.vivid.app.presentation.search.SearchUser
import com.vivid.app.presentation.stories.StoryViewerRoute

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    object Auth : Screen("auth", "Auth")
    object Feed : Screen("feed", "Inicio", Icons.Default.Home)
    object Search : Screen("search", "Buscar", Icons.Default.Search)
    object Create : Screen("create", "Crear", Icons.Default.Add)
    object Reels : Screen("reels", "Reels", Icons.Default.PlayArrow)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
    object OtherProfile : Screen("profile/{userId}", "Perfil")
    object Messages : Screen("messages", "Mensajes")
    object Chat : Screen("chat/{chatId}/{receiverId}/{receiverName}", "Chat")
    object FollowRequests : Screen("follow_requests", "Solicitudes")
    object CloseFriends : Screen("close_friends", "Mejores amigos")
    object BlockedUsers : Screen("blocked_users", "Bloqueados")
    object Settings : Screen("settings", "Ajustes")
}

@Composable
fun VividNavigation(navController: NavHostController) {
    val auth = FirebaseAuth.getInstance()
    val startDestination = remember(auth.currentUser?.uid) {
        if (auth.currentUser != null) Screen.Feed.route else Screen.Auth.route
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.Auth.route) {
                NavigationBar {
                    val items = listOf(Screen.Feed, Screen.Search, Screen.Create, Screen.Reels, Screen.Profile)
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon ?: Icons.Default.Home, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Feed.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Auth.route) {
                AuthScreen(onLoginSuccess = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Feed.route) {
                FeedScreen(
                    onOpenMessages = { navController.navigate(Screen.Messages.route) },
                    onOpenRequests = { navController.navigate(Screen.FollowRequests.route) },
                    onOpenProfile = { navController.navigate(Screen.Profile.route) },
                    onOpenStoryViewer = { storyId ->
                        navController.navigate("story_viewer/${Uri.encode(storyId)}")
                    }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onUserClick = { user -> navController.navigate("profile/${user.uid}") },
                    onMessageClick = { user -> navController.openChatWithUser(user) }
                )
            }
            composable(Screen.Create.route) {
                CreatePostScreen(navController = navController)
            }
            composable("camera") {
                CameraScreen(
                    onPhotoTaken = { uri ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("capturedPhoto", uri.toString())
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Reels.route) { ReelsScreen() }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                    onLogout = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onEditProfile = { navController.navigate("edit_profile") },
                    onSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(
                route = Screen.OtherProfile.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                ProfileScreen(
                    userId = userId,
                    onLogout = { navController.popBackStack() }, // Using onLogout as onBack here
                    onEditProfile = {},
                    onSettings = {},
                    onNavigateToChat = { chatId, receiverId, receiverName ->
                        navController.navigate("chat/${Uri.encode(chatId)}/${Uri.encode(receiverId)}/${Uri.encode(receiverName)}")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCloseFriends = { navController.navigate(Screen.CloseFriends.route) },
                    onOpenBlockedUsers = { navController.navigate(Screen.BlockedUsers.route) }
                )
            }
            composable("edit_profile") {
                EditProfileScreen(onSave = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
            }
            composable(Screen.Messages.route) {
                ChatListScreen(onChatClick = { chatId, receiverId, receiverName ->
                    navController.navigate(
                        "chat/${Uri.encode(chatId)}/${Uri.encode(receiverId)}/${Uri.encode(receiverName)}"
                    )
                })
            }
            composable(Screen.FollowRequests.route) {
                FollowRequestsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CloseFriends.route) {
                CloseFriendsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.BlockedUsers.route) {
                BlockedUsersScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType },
                    navArgument("receiverId") { type = NavType.StringType },
                    navArgument("receiverName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                val receiverId = backStackEntry.arguments?.getString("receiverId") ?: ""
                val receiverName = backStackEntry.arguments?.getString("receiverName") ?: "Usuario"
                ChatScreen(
                    chatId = chatId,
                    receiverId = receiverId,
                    otherUserName = receiverName,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { targetUserId ->
                        navController.navigate("profile/${Uri.encode(targetUserId)}")
                    }
                )
            }
            composable(
                route = "story_viewer/{storyId}",
                arguments = listOf(navArgument("storyId") { type = NavType.StringType })
            ) { backStackEntry ->
                val storyId = backStackEntry.arguments?.getString("storyId").orEmpty()
                StoryViewerRoute(
                    initialStoryId = storyId,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun NavHostController.openChatWithUser(user: SearchUser) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    if (currentUserId.isBlank() || user.uid.isBlank()) return
    val chatId = ChatRepository.buildChatId(currentUserId, user.uid)
    val name = user.displayName.ifBlank { user.username.ifBlank { "Usuario" } }
    // Store avatarBase64 and avatarUrl so ChatViewModel/ChatScreen can use it if needed
    previousBackStackEntry?.savedStateHandle?.set("avatarBase64", user.avatarBase64)
    previousBackStackEntry?.savedStateHandle?.set("avatarUrl", user.avatarUrl)
    navigate("chat/${Uri.encode(chatId)}/${Uri.encode(user.uid)}/${Uri.encode(name)}")
}
