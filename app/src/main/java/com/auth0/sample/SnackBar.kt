package com.auth0.sample

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

object SnackBar {
    fun show(ctx: Context, view: View, text: String) {
        Snackbar.make(
            view,
            text,
            Snackbar.LENGTH_LONG
        )
            .setTextColor(ContextCompat.getColor(ctx, R.color.white))
            .setBackgroundTint(ContextCompat.getColor(ctx, R.color.teal_200))
            .show()
    }
}