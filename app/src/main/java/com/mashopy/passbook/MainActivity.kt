/*
 * Copyright (C) 2025 Elias Gheeraert
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.mashopy.passbook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mashopy.passbook.ui.AboutScreen
import com.mashopy.passbook.ui.MainViewModel
import com.mashopy.passbook.ui.PassDetailScreen
import com.mashopy.passbook.ui.PassListScreen
import com.mashopy.passbook.ui.theme.PassbookTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            PassbookTheme {
                val navController = rememberNavController()

                Surface(color = MaterialTheme.colorScheme.background) {
                    NavHost(
                        navController = navController,
                        startDestination = "list",
                        enterTransition = {
                            fadeIn(tween(400)) + scaleIn(initialScale = 0.9f, animationSpec = tween(400))
                        },
                        exitTransition = {
                            fadeOut(tween(400))
                        },
                        popEnterTransition = {
                            fadeIn(tween(400)) + scaleIn(initialScale = 0.9f, animationSpec = tween(400))
                        },
                        popExitTransition = {
                            fadeOut(tween(400))
                        },
                    ) {
                        composable("list") {
                            PassListScreen(
                                viewModel = viewModel,
                                onPassClick = { id -> navController.navigate("detail/$id") },
                                onAboutClick = { navController.navigate("about") },
                            )
                        }
                        composable("detail/{passId}") { back ->
                            val id = back.arguments?.getString("passId")?.toLongOrNull()
                                ?: return@composable
                            PassDetailScreen(
                                passId = id,
                                viewModel = viewModel,
                                onBack = { navController.navigateUp() },
                            )
                        }
                        composable("about") {
                            AboutScreen(onBack = { navController.navigateUp() })
                        }
                    }
                }
            }
        }

        // Handle PDF shared directly into the app
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.type == "application/pdf") {
            intent.data?.let { viewModel.importPdf(it) }
        }
    }
}
