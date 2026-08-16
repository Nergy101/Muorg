package nl.muorg.android.ui.icon

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import nl.muorg.android.R

/**
 * Mage Icons (https://mageicons.com/, Apache-2.0) — the exact set the web
 * client vendors under `web-client/src/assets/mage-icons/`, converted to
 * Android vector drawables. Call sites use the web's own icon names so a
 * `.vue` template and a composable read the same, and so a glyph can never
 * drift between the two clients.
 *
 * The lookup is a compile-time map rather than `getIdentifier`, so a typo is a
 * missing key at runtime — handled below — and never a silently blank icon in
 * a release build where resource names have been stripped.
 */
private val MAGE_ICONS: Map<String, Int> = mapOf(
    "arrow-down" to R.drawable.ic_arrow_down,
    "arrow-left" to R.drawable.ic_arrow_left,
    "arrow-up" to R.drawable.ic_arrow_up,
    "arrowlist" to R.drawable.ic_arrowlist,
    "chart-up" to R.drawable.ic_chart_up,
    "check" to R.drawable.ic_check,
    "check-circle" to R.drawable.ic_check_circle,
    "chevron-down" to R.drawable.ic_chevron_down,
    "chevron-left" to R.drawable.ic_chevron_left,
    "chevron-up" to R.drawable.ic_chevron_up,
    "clock" to R.drawable.ic_clock,
    "color-swatch" to R.drawable.ic_color_swatch,
    "compact-disk" to R.drawable.ic_compact_disk,
    "compact-disk-fill" to R.drawable.ic_compact_disk_fill,
    "dash-menu" to R.drawable.ic_dash_menu,
    "dashboard" to R.drawable.ic_dashboard,
    "dashboard-fill" to R.drawable.ic_dashboard_fill,
    "dots" to R.drawable.ic_dots,
    "download" to R.drawable.ic_download,
    "edit" to R.drawable.ic_edit,
    "exchange" to R.drawable.ic_exchange,
    "eye" to R.drawable.ic_eye,
    "eye-off" to R.drawable.ic_eye_off,
    "github" to R.drawable.ic_github,
    "heart" to R.drawable.ic_heart,
    "home" to R.drawable.ic_home,
    "home-2" to R.drawable.ic_home_2,
    "home-2-fill" to R.drawable.ic_home_2_fill,
    "home-fill" to R.drawable.ic_home_fill,
    "information-circle" to R.drawable.ic_information_circle,
    "layout-grid" to R.drawable.ic_layout_grid,
    "moon" to R.drawable.ic_moon,
    "multiply" to R.drawable.ic_multiply,
    "music" to R.drawable.ic_music,
    "next" to R.drawable.ic_next,
    "note-text" to R.drawable.ic_note_text,
    "pause" to R.drawable.ic_pause,
    "pin" to R.drawable.ic_pin,
    "play" to R.drawable.ic_play,
    "playlist" to R.drawable.ic_playlist,
    "playlist-add" to R.drawable.ic_playlist_add,
    "plus" to R.drawable.ic_plus,
    "previous" to R.drawable.ic_previous,
    "refresh" to R.drawable.ic_refresh,
    "reload" to R.drawable.ic_reload,
    "save-floppy" to R.drawable.ic_save_floppy,
    "screencast" to R.drawable.ic_screencast,
    "search" to R.drawable.ic_search,
    "server" to R.drawable.ic_server,
    "settings" to R.drawable.ic_settings,
    "settings-fill" to R.drawable.ic_settings_fill,
    "stack" to R.drawable.ic_stack,
    "sun" to R.drawable.ic_sun,
    "trash" to R.drawable.ic_trash,
    "user" to R.drawable.ic_user,
    "wrench" to R.drawable.ic_wrench,
    "zap" to R.drawable.ic_zap,
    "zap-fill" to R.drawable.ic_zap_fill,
)

/** Resource id for a Mage icon name, falling back to the `music` glyph. */
@DrawableRes
fun mageIconRes(name: String): Int = MAGE_ICONS[name] ?: R.drawable.ic_music

@Composable
fun MageIcon(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(mageIconRes(name)),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
