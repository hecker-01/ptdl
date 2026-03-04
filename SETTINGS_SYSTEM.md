# Dynamic Settings System

This app features a modular, handler-based settings system that builds the UI dynamically from a JSON configuration file.

## Architecture Overview

The settings system uses three main components:

1. **JSON Configuration** (`settings.json`) - Defines all settings
2. **Setting Handlers** - Individual classes that implement setting functionality
3. **Settings Fragment** - Coordinates UI and delegates to handlers

## How It Works

### Configuration File

Settings are defined in `/app/src/main/assets/settings.json`. This file specifies:

- **Categories**: Sections with headers (e.g., "BUILD INFO", "APPEARANCE")
- **Items**: Individual settings within categories
- **Subpages**: Nested settings pages for complex options

### Setting Types

**1. Info Item** - Non-interactive display

```json
{
  "type": "info",
  "id": "build_number",
  "title": "Build Number",
  "subtitle": "Version info",
  "dynamic": true
}
```

- Use `"dynamic": true` for runtime updates (e.g., version number)

**2. Action Item** - Clickable item that triggers functionality

```json
{
  "type": "action",
  "id": "check_updates",
  "title": "Check for Updates",
  "subtitle": "Tap to check",
  "dynamic": true
}
```

- Requires a handler class to implement the action
- Use `"dynamic": true` for subtitle updates during operation

**3. Toggle Item** - Boolean switch

```json
{
  "type": "toggle",
  "id": "dark_mode",
  "title": "Dark Mode",
  "subtitle": "Enable dark theme",
  "key": "pref_dark_mode",
  "default": false
}
```

- Automatically saved to SharedPreferences using `key`
- No handler needed unless custom logic required

**4. Selection Item** - Single-choice options

```json
{
  "type": "selection",
  "id": "theme_color",
  "title": "Theme Color",
  "subtitle": "Choose accent color",
  "key": "pref_theme_color",
  "options": [
    { "value": "blue", "label": "Blue" },
    { "value": "red", "label": "Red" }
  ],
  "default": "blue"
}
```

- Shows a dialog with radio buttons
- Automatically saved to SharedPreferences

**5. Subpage Item** - Navigation to nested page

```json
{
  "type": "subpage",
  "id": "advanced_settings",
  "title": "Advanced Settings",
  "subtitle": "More options",
  "page": "advanced_page_id"
}
```

- Must reference a page defined in `subpages` section

### Complete JSON Structure

```json
{
  "categories": [
    {
      "id": "category_id",
      "title": "CATEGORY TITLE",
      "items": [
        {
          "type": "toggle",
          "id": "unique_setting_id",
          "title": "Setting Title",
          "subtitle": "Optional description",
          "key": "pref_key_name",
          "default": false
        }
      ]
    }
  ],
  "subpages": {
    "page_id": {
      "title": "Subpage Title",
      "categories": [
        {
          "id": "subpage_category",
          "title": "SUBPAGE SECTION",
          "items": [...]
        }
      ]
    }
  }
}
```

## Adding New Settings

### Simple Settings (No Custom Logic)

For **toggles** and **selections** that just need persistence:

1. **Edit `settings.json`** - Add to appropriate category:

```json
{
  "type": "toggle",
  "id": "my_setting",
  "title": "My Setting",
  "subtitle": "Description",
  "key": "pref_my_setting",
  "default": true
}
```

2. **Access in code**:

```kotlin
val settingsManager = SettingsManager(context)
val enabled = settingsManager.getBoolean("pref_my_setting", true)
```

That's it! No handler needed.

### Functional Settings (Custom Logic)

For **action items** or settings with special behavior:

**1. Create Handler Class** in `/settings/handlers/`:

```kotlin
package dev.heckr.ptdl.settings.handlers

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import dev.heckr.ptdl.settings.*

class SettingsMyFeature : SettingHandler {

    override val settingId: String = "my_feature_id"

    override fun handle(
        context: Context,
        fragment: Fragment,
        item: SettingItem,
        adapter: SettingsAdapter,
        settingsManager: SettingsManager
    ) {
        // Your custom logic here
        Toast.makeText(context, "Feature triggered!", Toast.LENGTH_SHORT).show()

        // Update UI if needed
        adapter.updateItemSubtitle(settingId, "Updated status")
    }

    override fun cleanup() {
        // Clean up resources if needed
    }
}
```

**2. Add to `settings.json`**:

```json
{
  "type": "action",
  "id": "my_feature_id",
  "title": "My Feature",
  "subtitle": "Tap to use",
  "dynamic": true
}
```

**3. Register in `SettingsFragment.initializeHandlers()`**:

```kotlin
private fun initializeHandlers() {
    // Existing handlers...
    updateAppHandler.initialize(this)
    handlers[updateAppHandler.settingId] = updateAppHandler

    // Add your handler
    val myHandler = SettingsMyFeature()
    handlers[myHandler.settingId] = myHandler
}
```

### Handler Initialization (When Needed)

Some handlers need initialization with Fragment context:

```kotlin
class SettingsMyFeature : SettingHandler {
    private var launcher: ActivityResultLauncher<Intent>? = null

    fun initialize(fragment: Fragment) {
        launcher = fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // Handle result
        }
    }

    // Then call initialize() in SettingsFragment
}
```

## Accessing Settings Values

### Reading Settings

```kotlin
val settingsManager = SettingsManager(context)

// Boolean toggle
val darkMode = settingsManager.getBoolean("pref_dark_mode", false)

// String selection
val themeColor = settingsManager.getString("pref_theme_color", "blue")
```

### Writing Settings

```kotlin
// Usually automatic via UI, but can be done manually:
settingsManager.setBoolean("pref_dark_mode", true)
settingsManager.setString("pref_theme_color", "purple")
```

### Listening for Changes

```kotlin
val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    when (key) {
        "pref_dark_mode" -> {
            // Update theme
        }
    }
}

settingsManager.registerChangeListener(listener)

// Don't forget to unregister
settingsManager.unregisterChangeListener(listener)
```

## File Structure

```
app/src/main/
├── assets/
│   └── settings.json                     # Settings configuration
├── java/net/heckerdev/ptdl/
│   ├── SettingsFragment.kt               # Main settings UI
│   └── settings/
│       ├── SettingsModels.kt             # Data models & JSON parser
│       ├── SettingsManager.kt            # SharedPreferences wrapper
│       ├── SettingsAdapter.kt            # RecyclerView adapter
│       └── handlers/
│           ├── SettingHandler.kt         # Base interface
│           ├── SettingsBuildNumber.kt    # Build info display
│           └── SettingsUpdateApp.kt      # Update checker
└── res/
    └── layout/
        ├── fragment_settings.xml         # Main layout
        ├── item_settings_category.xml    # Category header
        ├── item_settings_info.xml        # Info item
        ├── item_settings_action.xml      # Action button
        ├── item_settings_toggle.xml      # Toggle switch
        ├── item_settings_selection.xml   # Selection item
        └── item_settings_subpage.xml     # Subpage link
```

## Examples

### Example 1: Simple Toggle

**In `settings.json`:**

```json
{
  "type": "toggle",
  "id": "notifications",
  "title": "Enable Notifications",
  "key": "pref_notifications",
  "default": true
}
```

**Use in app:**

```kotlin
val notificationsEnabled = SettingsManager(context)
    .getBoolean("pref_notifications", true)

if (notificationsEnabled) {
    // Show notification
}
```

### Example 2: Custom Action Handler

**Create handler:**

```kotlin
class SettingsClearCache : SettingHandler {
    override val settingId = "clear_cache"

    override fun handle(context, fragment, item, adapter, settingsManager) {
        context.cacheDir.deleteRecursively()
        Toast.makeText(context, "Cache cleared!", Toast.LENGTH_SHORT).show()
    }
}
```

**Add to JSON:**

```json
{
  "type": "action",
  "id": "clear_cache",
  "title": "Clear Cache",
  "subtitle": "Free up storage space"
}
```

**Register:**

```kotlin
// In SettingsFragment.initializeHandlers()
val clearCacheHandler = SettingsClearCache()
handlers[clearCacheHandler.settingId] = clearCacheHandler
```

### Example 3: Subpage Navigation

**Define subpage in JSON:**

```json
{
  "categories": [...],
  "subpages": {
    "notifications_advanced": {
      "title": "Notification Settings",
      "categories": [
        {
          "id": "channels",
          "title": "CHANNELS",
          "items": [
            {
              "type": "toggle",
              "id": "messages_channel",
              "title": "Messages",
              "key": "pref_channel_messages",
              "default": true
            }
          ]
        }
      ]
    }
  }
}
```

**Link to it:**

```json
{
  "type": "subpage",
  "id": "notifications_link",
  "title": "Notification Settings",
  "page": "notifications_advanced"
}
```

## Tips & Best Practices

1. **Unique IDs**: Ensure all setting IDs are unique across the entire JSON
2. **Key Naming**: Use consistent prefix for preference keys (e.g., `pref_`)
3. **Dynamic Updates**: Set `"dynamic": true` only for items that update their subtitle at runtime
4. **Handler Cleanup**: Always implement `cleanup()` to prevent memory leaks
5. **Error Handling**: Handlers should handle errors gracefully and show user-friendly messages
6. **Testing**: Test handlers independently before integrating into settings

## Troubleshooting

**Setting not appearing?**

- Check JSON syntax (use a JSON validator)
- Verify the setting ID is unique
- Ensure category structure is correct

**Handler not firing?**

- Verify `settingId` matches the JSON `"id"` field exactly
- Confirm handler is registered in `initializeHandlers()`
- Check for exceptions in logcat

**Value not persisting?**

- Ensure `"key"` field is set in JSON
- Verify you're using the same key when reading
- Check SharedPreferences file exists (Device File Explorer)
