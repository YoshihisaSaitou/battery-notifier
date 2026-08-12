package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlin.math.roundToInt

@Composable
internal fun AdMobBanner(
    adUnitId: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val widthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
        val adView = remember(context, adUnitId, widthDp) {
            AdView(context).apply {
                this.adUnitId = adUnitId
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        context,
                        widthDp,
                    )
                )
            }
        }
        var isLoaded by remember(adView) { mutableStateOf(false) }

        DisposableEffect(adView) {
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    isLoaded = true
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoaded = false
                }
            }
            onDispose {
                (adView.parent as? ViewGroup)?.removeView(adView)
                adView.destroy()
            }
        }

        LaunchedEffect(adView) {
            adView.loadAd(AdRequest.Builder().build())
        }

        if (isLoaded) {
            AndroidView(
                factory = { adView },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .wrapContentHeight(),
            )
        }
    }
}
