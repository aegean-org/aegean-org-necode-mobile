package com.litter.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sigkitten.litter.android.R

@Composable
fun AnimatedSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LitterTheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandLogo(size = 116.dp, showWordmark = true)
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = LitterTheme.textMuted,
                style = TextStyle(
                    fontFamily = LitterTheme.monoFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                ),
            )
        }
    }
}
