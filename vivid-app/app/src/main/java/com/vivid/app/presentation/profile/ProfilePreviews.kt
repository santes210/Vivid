package com.vivid.app.presentation.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.vivid.app.domain.repository.FollowRelationshipState
import com.vivid.app.ui.preview.VividPreview
import com.vivid.app.ui.preview.VividPreviewA11y
import com.vivid.app.ui.preview.VividPreviewSurface

/**
 * Previews de la cabecera y las piezas del perfil.
 *
 * Cubren los cuatro estados que en la app real cuesta reproducir: perfil
 * propio, perfil ajeno sin seguir, solicitud pendiente y perfil privado
 * bloqueado.
 */

private fun sampleProfile(
    isPrivate: Boolean = false,
    isCurrentUser: Boolean = false
) = ProfileUiState(
    uid = "u1",
    username = "ana.paredes",
    displayName = "Ana Paredes",
    bio = "Fotografía de calle · Veracruz · Publico los martes",
    postsCount = 42,
    reelsCount = 7,
    followersCount = 1893,
    followingCount = 311,
    isPrivate = isPrivate,
    isCurrentUser = isCurrentUser
)

@VividPreviewA11y
@Composable
private fun ProfileHeaderOtherUserPreview() {
    VividPreviewSurface(padding = 0) {
        ProfileHeader(
            profile = sampleProfile(),
            isOwnProfile = false,
            relationshipState = FollowRelationshipState(),
            isFollowActionLoading = false,
            onToggleFollow = {},
            onEditProfile = {}
        )
    }
}

@VividPreview
@Composable
private fun ProfileHeaderOwnProfilePreview() {
    VividPreviewSurface(padding = 0) {
        ProfileHeader(
            profile = sampleProfile(isCurrentUser = true),
            isOwnProfile = true,
            relationshipState = FollowRelationshipState(),
            isFollowActionLoading = false,
            onToggleFollow = {},
            onEditProfile = {}
        )
    }
}

@VividPreview
@Composable
private fun ProfileHeaderPendingRequestPreview() {
    VividPreviewSurface(padding = 0) {
        ProfileHeader(
            profile = sampleProfile(isPrivate = true),
            isOwnProfile = false,
            relationshipState = FollowRelationshipState(
                hasPendingRequest = true,
                isTargetPrivate = true
            ),
            isFollowActionLoading = false,
            onToggleFollow = {},
            onEditProfile = {}
        )
    }
}

@VividPreview
@Composable
private fun PrivateProfileLockPreview() {
    VividPreviewSurface(padding = 0) {
        PrivateProfileLock(username = "ana.paredes", hasPendingRequest = false)
    }
}

@VividPreview
@Composable
private fun ProfileSkeletonPreview() {
    VividPreviewSurface(padding = 0) {
        Column {
            ProfileHeaderSkeleton()
        }
    }
}
