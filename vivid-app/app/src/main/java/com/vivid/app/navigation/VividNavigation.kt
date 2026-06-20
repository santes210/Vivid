package com.vivid.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vivid.app.presentation.auth.AuthScreen
import com.vivid.app.presentation.create.CameraScreen
import com.vivid.app.presentation.create.CreatePostScreen
import com.vivid.app.presentation.feed.FeedScreen
import com.vivid.app.presentation.messages.ChatListScreen
import com.vivid.app.presentation.messages.ChatScreen
import com.vivid.app.presentation.profile.ProfileScreen
import com.vivid.app.presentation.reels.ReelsScreen
import com.vivid.app.presentation.search.SearchScreen
import com.vivid.app.presentation.stories.StoriesScreen

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    object Auth : Screen("auth", "Auth")
    object Feed : Screen("feed", "Inicio", Icons.Default.Home)
    object Search : Screen("search", "Buscar", Icons.Default.Search)
    object Create : Screen("create", "Crear", Icons.Outlined.AddCircle)
    object Reels : Screen("reels", "Reels", Icons.Outlined.PlayArrow)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
    object Messages : Screen("messages", "Mensajes")
    object Chat : Screen("chat/{chatId}", "Chat")
}

@Composable
fun VividNavigation(navController: NavHostController) {
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
            startDestination = Screen.Auth.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Auth.route) {
                AuthScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Feed.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Feed.route) {
                FeedScreen(
                    onOpenMessages = { navController.navigate(Screen.Messages.route) },
                    onOpenProfile = { navController.navigate(Screen.Profile.route) }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onUserClick = { /* Navigate to profile */ },
                    onFollowClick = { /* Handle follow */ }
                )
            }
            composable(Screen.Create.route) {
                CreatePostScreen(navController = navController)
            }
            composable("camera") {
                CameraScreen(
                    onPhotoTaken = { uri ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("capturedPhoto", uri.toString())
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Reels.route) { ReelsScreen() }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onEditProfile = {
                        navController.navigate("edit_profile")
                    }
                )
            }
            composable("edit_profile") {
                EditProfileScreen(
                    onSave = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable("story_viewer/{index}") { backStackEntry ->
                val index = backStackEntry.arguments?.getString("index")?.toIntOrNull() ?: 0
                StoryViewerScreen(
                    stories = demoStories,
                    initialIndex = index,
                    onClose = { navController.popBackStack() }
                )
            }
            composable(Screen.Messages.route) {
                ChatListScreen(
                    onChatClick = { chatId ->
                        navController.navigate("chat/$chatId")
                    }
                )
            }
            composable(Screen.Chat.route) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                ChatScreen(chatId = chatId, otherUserName = "Usuario")
            }
        }
    }
}

@Composable
fun SearchScreenPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Pantalla de búsqueda (próximamente)")
    }
}

@Composable
fun ReelsScreenPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Reels (próximamente)")
    }
}