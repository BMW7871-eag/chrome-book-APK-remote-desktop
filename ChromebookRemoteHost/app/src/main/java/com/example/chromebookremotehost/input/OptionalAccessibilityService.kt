package com.example.chromebookremotehost.input

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * This service is deliberately not used to inject arbitrary global input.
 * ChromeOS/ARC does not grant an ordinary Android app a general-purpose desktop
 * mouse/keyboard injection API. Enabling this service does not bypass that boundary.
 */
class OptionalAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
