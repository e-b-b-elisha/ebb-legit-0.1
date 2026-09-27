package com.ebb.jarvis

import android.app.Application

/**
 * Nothing to set up at process start on purpose: a home screen has to be on screen
 * before the user's thumb leaves the button, so the API client, the app index, and
 * text-to-speech are all built lazily on first use.
 */
class JarvisApp : Application()
