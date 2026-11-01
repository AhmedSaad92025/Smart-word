package com.smartword.app.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The same open-and-show tests under the conditions of a real phone: Arabic system language, dark mode, phone screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-rEG-w360dp-h800dp-xhdpi")
class ArabicPhoneTest : OpenAndShowDocumentTest()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-night-xhdpi")
class DarkPhoneTest : OpenAndShowDocumentTest()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rEG-w360dp-h800dp-night-xhdpi")
class ArabicDarkPhoneApi35Test : OpenAndShowDocumentTest()
