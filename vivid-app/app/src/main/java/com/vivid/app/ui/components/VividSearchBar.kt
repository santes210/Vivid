package com.vivid.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividFeedbackTokens
import com.vivid.app.theme.VividSpace

/**
 * SearchBar / DockedSearchBar de Material 3 con la piel de Vivid.
 *
 * En teléfono [SearchBar] se expande a pantalla completa (animación del
 * propio componente). En tableta [DockedSearchBar] abre un panel anclado
 * debajo del campo, sin tapar el grid. El caller decide cuál con [docked].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VividSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    docked: Boolean = false,
    placeholder: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val colors = SearchBarDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dividerColor = MaterialTheme.colorScheme.outlineVariant
    )
    val shape = if (expanded) {
        VividExpressiveShapes.SearchBarActive
    } else {
        VividExpressiveShapes.SearchBar
    }
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            leadingIcon = {
                if (expanded) {
                    IconButton(onClick = { onExpandedChange(false) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search)
                    )
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close)
                        )
                    }
                }
            },
            colors = colors.inputFieldColors
        )
    }
    val barModifier = if (expanded && !docked) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .padding(horizontal = VividSpace.s, vertical = VividSpace.xs)
            .widthIn(max = VividFeedbackTokens.SnackbarMaxWidth)
            .fillMaxWidth()
    }

    if (docked) {
        DockedSearchBar(
            inputField = inputField,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = barModifier,
            shape = shape,
            colors = colors,
            content = content
        )
    } else {
        SearchBar(
            inputField = inputField,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = barModifier,
            shape = shape,
            colors = colors,
            content = content
        )
    }
}
