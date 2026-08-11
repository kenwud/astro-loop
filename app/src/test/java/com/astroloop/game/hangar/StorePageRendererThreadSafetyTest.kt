package com.astroloop.game.hangar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * `crystalTileRect` is written every frame on the game thread (`StorePageRenderer.draw`) and now
 * read from the UI thread on every store tap (`HangarSurfaceView.handleStoreTap`, tile 9's hit
 * test). Without `@Volatile` the UI thread has no guarantee of seeing a recent write — a stale or
 * torn rect would make tile 9 miss taps or hit-test against a rect from a previous layout.
 *
 * A behavioural test cannot observe a visibility bug (both threads usually agree by luck under
 * test), so this pins the actual JVM-level field modifier instead.
 */
class StorePageRendererThreadSafetyTest {

    @Test
    fun `crystalTileRect is volatile because the game thread writes it and the UI thread reads it`() {
        val field = StorePageRenderer::class.java.getDeclaredField("crystalTileRect")

        assertTrue(
            "crystalTileRect crosses threads and must be @Volatile",
            Modifier.isVolatile(field.modifiers)
        )
    }
}
