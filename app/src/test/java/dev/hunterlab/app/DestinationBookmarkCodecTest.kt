package dev.hunterlab.app

import dev.hunterlab.app.settings.DestinationBookmarkCodec
import dev.hunterlab.app.ui.contract.DestinationUi
import org.junit.Assert.*
import org.junit.Test

class DestinationBookmarkCodecTest {
    @Test fun legacyRecentRowsRemainReadable() {
        val place = DestinationBookmarkCodec.decode("-8.28|-35.97|Destino salvo")!!
        assertEquals("Destino salvo", place.label)
        assertEquals(-8.28, place.lat, 0.0)
        assertNull(place.detail)
    }
    @Test fun commercialNameAndAddressSurviveNewSchema() {
        val place = DestinationUi("Shopping de teste", -8.3, -35.95, "Via pública, Caruaru")
        assertEquals(place, DestinationBookmarkCodec.decode(DestinationBookmarkCodec.encode(place)))
    }
    @Test fun invalidCoordinatesDoNotEnterMapOrFavorites() {
        for (line in listOf("NaN|0|Teste", "91|0|Teste", "0|181|Teste", "bad-row")) {
            assertNull(DestinationBookmarkCodec.decode(line))
        }
    }
    @Test fun transientDistanceAndSeparatorsAreNotPersisted() {
        val encoded = DestinationBookmarkCodec.encode(DestinationUi("Teste|nome\n", 0.0, 0.0, "Rua|teste", "123 m em linha reta"))
        assertFalse(encoded.contains("123 m"))
        assertFalse(encoded.contains('\n'))
        assertEquals("Teste/nome ", DestinationBookmarkCodec.decode(encoded)!!.label)
    }
}
