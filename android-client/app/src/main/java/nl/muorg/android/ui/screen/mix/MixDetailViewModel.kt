package nl.muorg.android.ui.screen.mix

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.repository.Mix
import nl.muorg.android.data.repository.MixRepository
import javax.inject.Inject

/**
 * Reads the session's mixes rather than generating any: the lineup shown on
 * Home and the one opened here must be the same, or tapping a tile would land
 * on a different set of tracks than the tile advertised.
 */
@HiltViewModel
class MixDetailViewModel @Inject constructor(
    private val mixRepository: MixRepository,
) : ViewModel() {

    fun mix(id: Int): Mix? = mixRepository.byId(id)

    fun tracks(id: Int): List<CatalogTrack> =
        mixRepository.byId(id)?.let(mixRepository::tracksFor).orEmpty()
}
