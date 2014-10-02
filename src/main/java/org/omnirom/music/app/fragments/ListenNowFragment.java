/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.lucasr.twowayview.TwoWayView;
import org.lucasr.twowayview.widget.DividerItemDecoration;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.ListenNowAdapter;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A simple {@link Fragment} subclass showing ideas of tracks and albums to listen to.
 * Use the {@link ListenNowFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class ListenNowFragment extends Fragment implements ILocalCallback {

    private static final String TAG = "ListenNowFragment";

    private ListenNowAdapter mAdapter;
    private Handler mHandler;
    private TextView mTxtNoMusic;
    private static boolean sWarmUp = false;
    private int mWarmUpCount = 0;

    /**
     * Runnable responsible of generating the entries to put in the grid
     */
    private Runnable mGenerateEntries = new Runnable() {
        @Override
        public void run() {
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            final List<Playlist> playlists = aggregator.getAllPlaylists();
            final List<String> chosenSongs = new ArrayList<String>();

            int totalSongsCount = 0;

            sWarmUp = true;

            if (playlists.size() <= 0) {
                mTxtNoMusic.setVisibility(View.VISIBLE);
                if (mWarmUpCount < 2) {
                    mTxtNoMusic.setText(R.string.loading);
                } else {
                    mTxtNoMusic.setText(R.string.no_music_hint);
                }

                mWarmUpCount++;

                mHandler.postDelayed(this, 1000);
                return;
            } else {
                mTxtNoMusic.setVisibility(View.GONE);
            }

            for (Playlist p : playlists) {
                totalSongsCount += p.getSongsCount();
            }

            // We use a random algorithm (picking random tracks and albums and artists from
            // playlist) with a fixed layout:
            // - One big entry
            // - Six small entries
            // A total of 21 entries

            Random random = new Random(SystemClock.uptimeMillis());
            long startTime = SystemClock.uptimeMillis();
            for (int i = 0; i < 21; i++) {
                // Watchdog timer
                if (SystemClock.uptimeMillis() - startTime > 1000) {
                    break;
                }

                // Make sure we haven't reached all our accessible data
                if (chosenSongs.size() >= totalSongsCount) {
                    break;
                }

                // First, we determine the entity we want to show
                int type = random.nextInt(2);
                int playlistId = random.nextInt(playlists.size());

                Playlist playlist = playlists.get(playlistId);
                if (playlist.getSongsCount() <= 0) {
                    // Playlist is empty, skip to next one
                    i--;
                    continue;
                }

                int trackId = random.nextInt(playlist.getSongsCount());
                final ProviderIdentifier provider = playlist.getProvider();
                if (provider == null) {
                    Log.e(TAG, "Playlist has no identifier!");
                    continue;
                }

                String trackRef = playlist.songsList().get(trackId);
                if (chosenSongs.contains(trackRef)) {
                    // We already picked that song
                    i--;
                    continue;
                } else {
                    chosenSongs.add(trackRef);

                    // Remove the playlist from our selection if we picked all the songs from it
                    if (chosenSongs.containsAll(playlist.songsList())) {
                        playlists.remove(playlist);
                    }
                }

                Song track = aggregator.retrieveSong(trackRef, provider);
                if (track == null) {
                    // Some error while loading this track! Try another
                    i--;
                    continue;
                }

                // Now that we have the entity, let's figure if it's a big or small entry
                boolean isLarge = ((i % 7) == 0);

                // And we make the entry!
                BoundEntity entity;
                switch (type) {
                    case 0: // Artist
                        String artistRef = track.getArtist();
                        entity = aggregator.retrieveArtist(artistRef, track.getProvider());
                        break;

                    case 1: // Album
                        String albumRef = track.getAlbum();
                        entity = aggregator.retrieveAlbum(albumRef, track.getProvider());
                        ProviderConnection pc = PluginsLookup.getDefault()
                                .getProvider(provider);
                        if (pc != null) {
                            IMusicProvider binder = pc.getBinder();
                            try {
                                if (binder != null) {
                                    binder.fetchAlbumTracks(albumRef);
                                }
                            } catch (RemoteException e) {
                                // ignore
                            }
                        }
                        break;

                    case 2: // Song
                        entity = track;
                        break;

                    default:
                        Log.e(TAG, "Unexpected entry type " + type);
                        entity = null;
                        break;
                }

                if (entity != null) {
                    ListenNowAdapter.ListenNowEntry entry = new ListenNowAdapter.ListenNowEntry(
                            isLarge ? ListenNowAdapter.ListenNowEntry.ENTRY_SIZE_LARGE
                                    : ListenNowAdapter.ListenNowEntry.ENTRY_SIZE_MEDIUM,
                            entity);
                    mAdapter.addEntry(entry);
                    mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);
                } else {
                    // Something bad happened while getting this entity, try something else
                    i--;
                }
            }
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment ListenNowFragment.
     */
    public static ListenNowFragment newInstance() {
        return new ListenNowFragment();
    }

    /**
     * Default empty constructor
     */
    public ListenNowFragment() {
        // Required empty public constructor
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        mAdapter = new ListenNowAdapter();

        // Generate entries
        if (!sWarmUp) {
            mHandler.postDelayed(mGenerateEntries, 500);
        } else {
            mHandler.post(mGenerateEntries);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        FrameLayout root = (FrameLayout) inflater.inflate(R.layout.fragment_listen_now, container, false);
        TwoWayView twvRoot = (TwoWayView) root.findViewById(R.id.twvRoot);
        mTxtNoMusic = (TextView) root.findViewById(R.id.txtNoMusic);

        twvRoot.setAdapter(mAdapter);
        final Drawable divider = getResources().getDrawable(R.drawable.divider);
        twvRoot.addItemDecoration(new DividerItemDecoration(divider));
        twvRoot.setItemAnimator(new DefaultItemAnimator());

        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_LISTEN_NOW);
        mainActivity.setContentShadowTop(0);
        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDetach() {
        super.onDetach();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSongUpdate(final List<Song> s) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int hasThisSong;
                for (Song song : s) {
                    hasThisSong = mAdapter.contains(song);
                    if (hasThisSong >= 0) {
                        mAdapter.notifyItemChanged(hasThisSong);
                    }
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAlbumUpdate(final List<Album> a) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int hasThisAlbum;
                for (Album album : a) {
                    hasThisAlbum = mAdapter.contains(album);
                    if (hasThisAlbum >= 0) {
                        mAdapter.notifyItemChanged(hasThisAlbum);
                    }
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPlaylistUpdate(List<Playlist> p) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onArtistUpdate(final List<Artist> a) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int hasThisArtist;
                for (Artist artist : a) {
                    hasThisArtist = mAdapter.contains(artist);
                    if (hasThisArtist >= 0) {
                        mAdapter.notifyItemChanged(hasThisArtist);
                    }
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProviderConnected(IMusicProvider provider) {
        if (sWarmUp) {
            mHandler.removeCallbacks(mGenerateEntries);
            mHandler.post(mGenerateEntries);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSearchResult(SearchResult searchResult) {
    }
}
