package org.osmdroid.tileprovider.modules;

import java.io.File;

import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.OpenStreetMapTile;
import org.osmdroid.tileprovider.OpenStreetMapTileRequestState;
import org.osmdroid.tileprovider.tilesource.IOpenStreetMapRendererInfo;
import org.osmdroid.tileprovider.tilesource.OpenStreetMapRendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.graphics.drawable.Drawable;

/**
 * Implements a file system cache and provides cached tiles. This functions as a tile provider by
 * serving cached tiles for the supplied tile source.
 * 
 * @author Marc Kurtz
 * @author Nicolas Gramlich
 * 
 */
public class OpenStreetMapTileFilesystemProvider extends OpenStreetMapTileFileStorageProviderBase {

	// ===========================================================
	// Constants
	// ===========================================================

	private static final Logger logger = LoggerFactory
			.getLogger(OpenStreetMapTileFilesystemProvider.class);

	// ===========================================================
	// Fields
	// ===========================================================

	private final long mMaximumCachedFileAge;

	private IOpenStreetMapRendererInfo mTileSource;

	// ===========================================================
	// Constructors
	// ===========================================================

	public OpenStreetMapTileFilesystemProvider(final IRegisterReceiver aRegisterReceiver) {
		this(aRegisterReceiver, OpenStreetMapRendererFactory.DEFAULT_TILE_SOURCE);
	}

	public OpenStreetMapTileFilesystemProvider(final IRegisterReceiver aRegisterReceiver,
			final IOpenStreetMapRendererInfo aTileSource) {
		this(aRegisterReceiver, aTileSource, DEFAULT_MAXIMUM_CACHED_FILE_AGE);
	}

	/**
	 * Provides a file system based cache tile provider. Other providers can register and store data
	 * in the cache.
	 * 
	 * @param aRegisterReceiver
	 */
	public OpenStreetMapTileFilesystemProvider(final IRegisterReceiver aRegisterReceiver,
			final IOpenStreetMapRendererInfo aTileSource, final long maximumCachedFileAge) {
		super(NUMBER_OF_TILE_FILESYSTEM_THREADS, TILE_FILESYSTEM_MAXIMUM_QUEUE_SIZE,
				aRegisterReceiver);
		mTileSource = aTileSource;

		mMaximumCachedFileAge = maximumCachedFileAge;
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	@Override
	public boolean getUsesDataConnection() {
		return false;
	}

	@Override
	protected String getName() {
		return "File System Cache Provider";
	}

	@Override
	protected String getThreadGroupName() {
		return "filesystem";
	}

	@Override
	protected Runnable getTileLoader() {
		return new TileLoader();
	};

	@Override
	public int getMinimumZoomLevel() {
		return (mTileSource != null ? mTileSource.getMinimumZoomLevel() : Integer.MAX_VALUE);
	}

	@Override
	public int getMaximumZoomLevel() {
		return (mTileSource != null ? mTileSource.getMaximumZoomLevel() : Integer.MIN_VALUE);
	}

	@Override
	public void setTileSource(IOpenStreetMapRendererInfo pTileSource) {
		mTileSource = pTileSource;
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	private class TileLoader extends OpenStreetMapAsyncTileProvider.TileLoader {

		@Override
		public Drawable loadTile(final OpenStreetMapTileRequestState aState) {

			if (mTileSource == null)
				return null;

			final OpenStreetMapTile aTile = aState.getMapTile();

			// if there's no sdcard then don't do anything
			if (!getSdCardAvailable()) {
				if (DEBUGMODE)
					logger.debug("No sdcard - do nothing for tile: " + aTile);
				return null;
			}

			// Check the tile source to see if its file is available and if so, then render the
			// drawable and return the tile
			final File file = new File(TILE_PATH_BASE,
					mTileSource.getTileRelativeFilenameString(aTile));
			if (file.exists()) {

				// Check to see if file has expired
				final long now = System.currentTimeMillis();
				final long lastModified = file.lastModified();
				final boolean fileExpired = lastModified < now - mMaximumCachedFileAge;

				if (!fileExpired) {
					// If the file has not expired, then render it and return it!
					final Drawable drawable = mTileSource.getDrawable(file.getPath());
					return drawable;
				} else {
					// If the file has expired then we render it, but we return it as a candidate
					// and then fail on the request. This allows the tile to be loaded, but also
					// allows other tile providers to do a better job.
					final Drawable drawable = mTileSource.getDrawable(file.getPath());
					tileCandidateLoaded(aState, drawable);
					return null;
				}
			}

			// If we get here then there is no file in the file cache
			return null;
		}
	}
}