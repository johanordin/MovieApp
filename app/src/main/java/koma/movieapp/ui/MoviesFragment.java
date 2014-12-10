/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package koma.movieapp.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.content.AsyncTaskLoader;
import android.support.v4.view.ViewCompat;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.uwetrottmann.tmdb.Tmdb;
import com.uwetrottmann.tmdb.entities.Movie;
import com.uwetrottmann.tmdb.entities.ResultsPage;
import com.uwetrottmann.tmdb.services.MoviesService;

import koma.movieapp.util.ImageLoader;

import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.ListPreloader;

import koma.movieapp.Config;
import koma.movieapp.R;
import koma.movieapp.ui.widget.CollectionView;
import koma.movieapp.ui.widget.CollectionViewCallbacks;
import koma.movieapp.ui.widget.MessageCardView;
import koma.movieapp.util.PrefUtils;
import koma.movieapp.util.UIUtils;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import static koma.movieapp.util.LogUtils.LOGD;
import static koma.movieapp.util.LogUtils.LOGE;
import static koma.movieapp.util.LogUtils.LOGV;
import static koma.movieapp.util.LogUtils.LOGW;
import static koma.movieapp.util.LogUtils.makeLogTag;

/**
 * A {@link ListFragment} showing a list of sessions. The fragment arguments
 * indicate what is the list of sessions to show. It may be a set of tag
 * filters or a search query.
 */
public class MoviesFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<ArrayList<Movie>>, CollectionViewCallbacks {

    private static final String TAG = makeLogTag(MoviesFragment.class);

    // Disable track branding
    public static final String EXTRA_NO_TRACK_BRANDING =
            "com.google.android.iosched.extra.NO_TRACK_BRANDING";

    private static final String STATE_SESSION_QUERY_TOKEN = "session_query_token";
    private static final String STATE_ARGUMENTS = "arguments";

    /**
     * The handler message for updating the search query.
     */
    private static final int MESSAGE_QUERY_UPDATE = 1;
    /**
     * The delay before actual requerying in millisecs.
     */
    private static final int QUERY_UPDATE_DELAY_MILLIS = 100;
    /**
     * The number of rows ahead to preload images for
     */
    private static final int ROWS_TO_PRELOAD = 2;

    private static final int ANIM_DURATION = 250;
    private static final int CARD_DISMISS_ACTION_DELAY = MessageCardView.ANIM_DURATION - 50;

    private Context mAppContext;

    //private ImageLoader mImageLoader;

    // the cursor whose data we are currently displaying
    private int mSessionQueryToken;
    //private Uri mCurrentUri = ScheduleContract.Sessions.CONTENT_URI;
    private Cursor mCursor;

    private ArrayList<Movie> mMovieList;

    private boolean mIsSearchCursor;
    private boolean mNoTrackBranding;

    // this variable is relevant when we start the sessions loader, and indicates the desired
    // behavior when load finishes: if true, this is a full reload (for example, because filters
    // have been changed); if not, it's just a refresh because data has changed.
    private boolean mSessionDataIsFullReload = false;

    private ImageLoader mImageLoader;
    private int mDefaultMovieColor;

    private CollectionView mCollectionView;
    private TextView mEmptyView;
    private View mLoadingView;

    private boolean mWasPaused = false;

    private static final int HERO_GROUP_ID = 123;

    private Bundle mArguments;

    private DateFormat mDateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
    private DateFormat mTimeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);

    private static final String CARD_ANSWER_ATTENDING_REMOTELY = "CARD_ANSWER_ATTENDING_REMOTELY";
    private static final String CARD_ANSWER_ATTENDING_IN_PERSON = "CARD_ANSWER_ATTENDING_IN_PERSON";
    private static final String CARD_ANSWER_YES = "CARD_ANSWER_YES";
    private static final String CARD_ANSWER_NO = "CARD_ANSWER_NO";

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            if (msg.what == MESSAGE_QUERY_UPDATE) {
                String query = (String) msg.obj;
                //reloadFromArguments(BaseActivity.intentToFragmentArguments(
                //        new Intent(Intent.ACTION_SEARCH, ScheduleContract.Sessions.buildSearchUri(query))));*/
            }
        }

    };

    private Preloader mPreloader;


    public boolean canCollectionViewScrollUp() {
        return ViewCompat.canScrollVertically(mCollectionView, -1);
    }

    public void setContentTopClearance(int topClearance) {
        mCollectionView.setContentTopClearance(topClearance);
    }

    // Called when there is a change on sessions in the content provider
    private void onSessionsContentChanged() {
        LOGD(TAG, "ThrottledContentObserver fired (sessions). Content changed.");
        if (!isAdded()) {
            LOGD(TAG, "Ignoring ContentObserver event (Fragment not added).");
            return;
        }

        LOGD(TAG, "Requesting sessions cursor reload as a result of ContentObserver firing.");
        reloadMovieData(false);
    }

    // TODO fix reload
    private void reloadMovieData(boolean fullReload) {
        LOGD(TAG, "Reloading session data: " + (fullReload ? "FULL RELOAD" : "light refresh"));
        mSessionDataIsFullReload = fullReload;

        getLoaderManager().restartLoader(mSessionQueryToken, mArguments, MoviesFragment.this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mWasPaused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWasPaused) {
            mWasPaused = false;
            LOGD(TAG, "Reloading data as a result of onResume()");
            reloadMovieData(false);
        }
    }

    public interface Callbacks {
        public void onSessionSelected(String sessionId, View clickedView);
    }

    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onSessionSelected(String sessionId, View clickedView) {
        }

    };

    private Callbacks mCallbacks = sDummyCallbacks;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//
        if (mImageLoader == null) {
            mImageLoader = new ImageLoader(this.getActivity());
        }

        mDefaultMovieColor = getResources().getColor(R.color.default_movie_color);

        final TimeZone tz = PrefUtils.getDisplayTimeZone(getActivity());
        mDateFormat.setTimeZone(tz);
        mTimeFormat.setTimeZone(tz);

        if (savedInstanceState != null) {
            mSessionQueryToken = savedInstanceState.getInt(STATE_SESSION_QUERY_TOKEN);

            if (mSessionQueryToken > 0) {
                // Only if this is a config change should we initLoader(), to reconnect with an
                // existing loader. Otherwise, the loader will be init'd when reloadFromArguments
                // is called.

                getLoaderManager().initLoader(mSessionQueryToken, null, MoviesFragment.this);
                //getLoaderManager().initLoader(0, null, this);


            }
        }

    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mAppContext = getActivity().getApplicationContext();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.fragment_movies, container, false);
        mCollectionView = (CollectionView) root.findViewById(R.id.movies_collection_view);
        mPreloader = new Preloader(ROWS_TO_PRELOAD);
        mCollectionView.setOnScrollListener(mPreloader);
        mEmptyView = (TextView) root.findViewById(R.id.empty_text);
        mLoadingView = root.findViewById(R.id.loading);
        return root;
    }

    void reloadFromArguments(Bundle arguments) {
        // Load new arguments
        if (arguments == null) {
            arguments = new Bundle();
        } else {
            // since we might make changes, don't meddle with caller's copy
            arguments = (Bundle) arguments.clone();
        }

        // save arguments so we can reuse it when reloading from content observer events
        mArguments = arguments;

        /*LOGD(TAG, "MoviesFragment reloading from arguments: " + arguments);
        mCurrentUri = arguments.getParcelable("_uri");
        if (mCurrentUri == null) {
            // if no URI, default to all sessions URI
            LOGD(TAG, "MoviesFragment did not get a URL, defaulting to all sessions.");
            //arguments.putParcelable("_uri", ScheduleContract.Sessions.CONTENT_URI);
            //mCurrentUri = ScheduleContract.Sessions.CONTENT_URI;
        }*/

        mNoTrackBranding = mArguments.getBoolean(EXTRA_NO_TRACK_BRANDING);


        //LOGD(TAG, "MoviesFragment reloading, uri=" + mCurrentUri);

        reloadMovieData(true); // full reload

    }

    void requestQueryUpdate(String query) {
        mHandler.removeMessages(MESSAGE_QUERY_UPDATE);
        mHandler.sendMessageDelayed(Message.obtain(mHandler, MESSAGE_QUERY_UPDATE, query),
                QUERY_UPDATE_DELAY_MILLIS);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callbacks)) {
            throw new ClassCastException("Activity must implement fragment's callbacks.");
        }

        mAppContext = getActivity().getApplicationContext();
        mCallbacks = (Callbacks) activity;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        sp.registerOnSharedPreferenceChangeListener(mPrefChangeListener);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = sDummyCallbacks;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sp.unregisterOnSharedPreferenceChangeListener(mPrefChangeListener);
    }

    public void animateReload() {
        //int curTop = mCollectionView.getTop();
        mCollectionView.setAlpha(0);
        //mCollectionView.setTop(getResources().getDimensionPixelSize(R.dimen.browse_sessions_anim_amount));
        //mCollectionView.animate().y(curTop).alpha(1).setDuration(ANIM_DURATION).setInterpolator(new DecelerateInterpolator());
        mCollectionView.animate().alpha(1).setDuration(ANIM_DURATION).setInterpolator(new DecelerateInterpolator());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SESSION_QUERY_TOKEN, mSessionQueryToken);
        outState.putParcelable(STATE_ARGUMENTS, mArguments);
    }

    // LoaderCallbacks interface
    @Override
    public Loader<ArrayList<Movie>> onCreateLoader(int id, Bundle data) {
        LOGD(TAG, "onCreateLoader, id=" + id + ", data=" + data);
        final Intent intent = BaseActivity.fragmentArgumentsToIntent(data);
        Uri sessionsUri = intent.getData();

        return new MovieListLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<ArrayList<Movie>> loader, ArrayList<Movie> data) {

        if (getActivity() == null) {
            return;
        }

        int token = loader.getId();

        LOGD(TAG, "Loader finished: " + (token == NOW_PLAYING_TOKEN ? "now playing" :
                token == UPCOMING_TOKEN ? "upcoming" : token == POPULAR_TOKEN ? "popular" : "unknown"));

        if (token == POPULAR_TOKEN || token == NOW_PLAYING_TOKEN || token == UPCOMING_TOKEN) {

            mMovieList = data;

            LOGD(TAG, "Will now update collection view.");
            updateCollectionView();

        }

    }

    @Override
    public void onLoaderReset(Loader<ArrayList<Movie>> loader) {
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (isAdded()) {
                        if (PrefUtils.PREF_LOCAL_TIMES.equals(key)) {
                            updateCollectionView();
                        } else if (PrefUtils.PREF_ATTENDEE_AT_VENUE.equals(key)) {
                            if (mCursor != null) {
                                reloadMovieData(true);
                            }
                        }
                    }
                }
            };

    private void updateCollectionView() {
        if (mMovieList.isEmpty()) {
            LOGD(TAG, "updateCollectionView: not ready yet... no iterator");
            // not ready
            return;
        }

        LOGD(TAG, "MoviesFragment updating CollectionView... " + (mSessionDataIsFullReload ?
                "(FULL RELOAD)" : "(light refresh)"));

        mMaxDataIndexAnimated = 0;

        CollectionView.Inventory inv;

        if (!mMovieList.isEmpty()) {
            hideEmptyView();
            inv = prepareInventory();
        } else {
            showEmptyView();
            inv = new CollectionView.Inventory();
        }


        Parcelable state = null;
        if (!mSessionDataIsFullReload) {
            // it's not a full reload, so we want to keep scroll position, etc
            state = mCollectionView.onSaveInstanceState();
        }
        mCollectionView.setCollectionAdapter(this);
        mCollectionView.updateInventory(inv, mSessionDataIsFullReload);
        if (state != null) {
            mCollectionView.onRestoreInstanceState(state);
        }
        mSessionDataIsFullReload = false;
    }

    private void hideEmptyView() {
        mEmptyView.setVisibility(View.GONE);
        mLoadingView.setVisibility(View.GONE);
    }

    private void showEmptyView() {

//        final String searchQuery = ScheduleContract.Sessions.isSearchUri(mCurrentUri) ?
//                ScheduleContract.Sessions.getSearchQuery(mCurrentUri) : null;
        mEmptyView.setText("");
        mEmptyView.setVisibility(View.VISIBLE);
        mLoadingView.setVisibility(View.GONE);

    }


    // Creates the CollectionView groups based on the cursor data.
    private CollectionView.Inventory prepareInventory() {
        LOGD(TAG, "Preparing collection view inventory.");


        ArrayList<CollectionView.InventoryGroup> mainGroup =
                new ArrayList<CollectionView.InventoryGroup>();
        HashMap<String, CollectionView.InventoryGroup> mainGroupName =
                new HashMap<String, CollectionView.InventoryGroup>();

        int dataIndex = -1;

        final boolean expandedMode = true;

        int nextGroupId = HERO_GROUP_ID + 1000;


        final int displayCols = getResources().getInteger(R.integer.explore_2nd_level_grid_columns);

        mPreloader.setDisplayCols(displayCols);


//        ArrayList<CollectionView.InventoryGroup> list = mainGroup;
//        HashMap<String, CollectionView.InventoryGroup> map = mainGroupName;
//
//        CollectionView.InventoryGroup group =
//                new CollectionView.InventoryGroup(0)
//                        .setDisplayCols(displayCols);

        for(Movie movie : mMovieList) {

            ++dataIndex;

            String groupLabel;

            groupLabel = "Movies";

            CollectionView.InventoryGroup group;


            // "list" and "map" are just shorthand variables pointing to the right list and map
            ArrayList<CollectionView.InventoryGroup> list = mainGroup;
            HashMap<String, CollectionView.InventoryGroup> map = mainGroupName;

            // Create group, if it doesn't exist yet
            if (!map.containsKey(groupLabel)) {
                LOGV(TAG, "Creating new group: " + groupLabel);
                group = new CollectionView.InventoryGroup(nextGroupId++)
                        .setDisplayCols(displayCols)
                        .setShowHeader(!TextUtils.isEmpty(groupLabel))
                        .setHeaderLabel(groupLabel);
                map.put(groupLabel, group);
                list.add(group);
            } else {
                LOGV(TAG, "Adding to existing group: " + groupLabel);
                group = map.get(groupLabel);
            }

            LOGV(TAG, "...adding to group '" + groupLabel + "' with custom data index " + dataIndex);
            group.addItemWithCustomDataIndex(dataIndex);


        }

        ArrayList<CollectionView.InventoryGroup> groups = new ArrayList<>();

        groups.addAll(mainGroup);

        CollectionView.Inventory inventory = new CollectionView.Inventory();
        for (CollectionView.InventoryGroup g : groups) {
            inventory.addGroup(g);
        }

        return inventory;


    }


    @Override
    public View newCollectionHeaderView(Context context, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.list_item_explore_header, parent, false);
    }

    @Override
    public void bindCollectionHeaderView(Context context, View view, int groupId, String groupLabel) {
        TextView tv = (TextView) view.findViewById(android.R.id.text1);
        if (tv != null) {
            tv.setText(groupLabel);
        }
    }


    @Override
    public View newCollectionItemView(Context context, int groupId, ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        int layoutId;
        layoutId = R.layout.list_item_movie;

//        if (useExpandedMode()) {
//            layoutId = R.layout.list_item_movie;
//        } else {
//            // Group HERO_GROUP_ID is the hero -- use a larger layout
//            layoutId = (groupId == HERO_GROUP_ID) ? R.layout.list_item_session_hero :
//                    R.layout.list_item_session_summarized;
//        }

        return inflater.inflate(layoutId, parent, false);
    }

    private StringBuilder mBuffer = new StringBuilder();

    private int mMaxDataIndexAnimated = 0;


    @Override
    public void bindCollectionItemView(Context context, View view, int groupId, int indexInGroup, int dataIndex, Object tag) {


        if (mMovieList.isEmpty() || mMovieList.get(dataIndex) == null) {
            return;
        }

        Movie movie = mMovieList.get(dataIndex);

        final String movieId = movie.id.toString();

        if (movieId == null) return;

        final String movieTitle = movie.title;
        final String movieRating = movie.vote_average.toString();
        final String movieBackdrop = movie.backdrop_path;

        System.out.println("Movie title in BindCollectionItemView: " + movieTitle);

        int movieColor = getResources().getColor(R.color.default_movie_color);
        int darkMovieColor = 0;

        final TextView titleView = (TextView) view.findViewById(R.id.movie_title);
        final TextView ratingView = (TextView) view.findViewById(R.id.movie_rating);

        final View movieTargetView = view.findViewById(R.id.movie_target);


        if (movieColor == 0) {
            movieColor = mDefaultMovieColor;
        }

        darkMovieColor = UIUtils.scaleMovieColorToDefaultBG(movieColor);

        ImageView photoView = (ImageView) view.findViewById(R.id.session_photo_colored);

        if (photoView != null) {
            if (!mPreloader.isDimensSet()) {
                final ImageView finalPhotoView = photoView;
                photoView.post(new Runnable() {
                    @Override
                    public void run() {
                        mPreloader.setDimens(finalPhotoView.getWidth(), finalPhotoView.getHeight());
                    }
                });
            }
//            // colored filter on the images
//            photoView.setColorFilter(mNoTrackBranding
//                    ? new PorterDuffColorFilter(
//                    getResources().getColor(R.color.no_track_branding_session_tile_overlay),
//                    PorterDuff.Mode.SRC_ATOP)
//                    : UIUtils.makeSessionImageScrimColorFilter(darkMovieColor));
        } else {
            photoView = (ImageView) view.findViewById(R.id.session_photo_colored);
        }
        ViewCompat.setTransitionName(photoView, "photo_" + movieId);

        // when we load a photo, it will fade in from transparent so the
        // background of the container must be the session color to avoid a white flash
        ViewParent parent = photoView.getParent();
        if (parent != null && parent instanceof View) {
            ((View) parent).setBackgroundColor(darkMovieColor);
        } else {
            photoView.setBackgroundColor(darkMovieColor);
        }

        // render title

        titleView.setTextColor(getResources().getColor(R.color.body_text_1_inverse));
        titleView.setBackgroundColor(getResources().getColor(R.color.material_blue_grey_800));

        titleView.setText(movieTitle == null ? "?" : movieTitle);

        // set the rating
        if (ratingView != null) {
            ratingView.setText(movieRating);
        }

        //photoView.setColorFilter(new PorterDuffColorFilter(getResources().getColor(R.color.data_item_background_with_alpha),PorterDuff.Mode.SCREEN));

        // set the images
        mImageLoader.loadImage(Config.TMDB_IMAGE_BASE_URL + "w780" + movieBackdrop, photoView);

        final View finalPhotoView = photoView;
        movieTargetView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallbacks.onSessionSelected(movieId, finalPhotoView);
            }
        });

        // animate this card
        if (dataIndex > mMaxDataIndexAnimated) {
            mMaxDataIndexAnimated = dataIndex;
        }

    }




/*
    private void setupLocalOrRemoteCard(final MessageCardView card) {
        card.setText(getString(R.string.question_local_or_remote));
        card.setButton(0, getString(R.string.attending_remotely), CARD_ANSWER_ATTENDING_REMOTELY,
                false, 0);
        card.setButton(1, getString(R.string.attending_in_person), CARD_ANSWER_ATTENDING_IN_PERSON,
                true, 0);
        final Context context = getActivity().getApplicationContext();
        final Activity activity = getActivity();
        card.setListener(new MessageCardView.OnMessageCardButtonClicked() {
            @Override
            public void onMessageCardButtonClicked(final String tag) {
                final boolean inPerson = CARD_ANSWER_ATTENDING_IN_PERSON.equals(tag);
                card.dismiss(true);

                if (activity != null) {
                    Toast.makeText(activity, inPerson ? R.string.explore_attending_in_person_toast
                            : R.string.explore_attending_remotely_toast, Toast.LENGTH_LONG).show();
                }

                // post delayed to give card time to animate
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        PrefUtils.setAttendeeAtVenue(context, inPerson);
                        PrefUtils.markAnsweredLocalOrRemote(context);
                    }
                }, CARD_DISMISS_ACTION_DELAY);
            }
        });
        card.show();
    }
*/


    private void animateSessionAppear(final View view) {
    }


    private class Preloader extends ListPreloader<String> {

        private int[] photoDimens;
        private int displayCols;

        public Preloader(int maxPreload) {
            super(maxPreload);
        }

        public void setDisplayCols(int displayCols) {
            this.displayCols = displayCols;
        }

        public boolean isDimensSet() {
            return photoDimens != null;
        }

        public void setDimens(int width, int height) {
            if (photoDimens == null) {
                photoDimens = new int[]{width, height};
            }
        }

        @Override
        protected int[] getDimensions(String s) {
            return photoDimens;
        }

        @Override
        protected List<String> getItems(int start, int end) {
            // Our start and end are rows, we need to adjust them into data columns
            // The keynote is 1 row with 1 data item, so we need to adjust.
            int keynoteDataOffset = (displayCols - 1);
            int dataStart = start * displayCols - keynoteDataOffset;
            int dataEnd = end * displayCols - keynoteDataOffset;
            List<String> urls = new ArrayList<String>();

            if (!mMovieList.isEmpty()) {
                for(Movie movie : mMovieList) {
                    String backdrop;
                    backdrop = movie.backdrop_path;
                    //LOGD(TAG, "PRELOADER getItems: Backdrop =  " + backdrop);

                    //System.out.println("PRELOADER getItems: Backdrop = " + backdrop);

                    urls.add(Config.TMDB_IMAGE_BASE_URL + "w780" + backdrop);

                }
            }

            return urls;

        }

        @Override
        protected GenericRequestBuilder getRequestBuilder(String url) {
            return mImageLoader.beginImageLoad(url, null, true /*crop*/);
        }
    }

    private static class MovieListLoader extends AsyncTaskLoader<ArrayList<Movie>> {

        ArrayList<Movie> mMovies;
        int apiID;
        Tmdb tmdb;

        public MovieListLoader(Context context) {
            super(context);
            //this.apiID = apiID;

            tmdb = new Tmdb();
            tmdb.setApiKey(Config.TMDB_API_KEY);
        }

        /**
         * This is where the bulk of our work is done.  This function is
         * called in a background thread and should generate a new set of
         * data to be published by the loader.
         */
        @Override
        public ArrayList<Movie> loadInBackground() {

            ArrayList<Movie> movieList = new ArrayList<>();
            MoviesService moviesService = null;
            ResultsPage resultsPage = null;

            try {
                moviesService = tmdb.moviesService();
                switch (this.getId()) {
                    case POPULAR_TOKEN:
                        resultsPage = moviesService.popular();
                        break;
                    case NOW_PLAYING_TOKEN:
                        resultsPage = moviesService.nowPlaying();
                        break;
                    case UPCOMING_TOKEN:
                        resultsPage = moviesService.upcoming();
                        break;
                    default:
                        break;

                }
                if (resultsPage != null) {


                    List<Movie> tempList = resultsPage.results;
                    for(int i = 0; i < tempList.size(); i++) {
                        movieList.add(tempList.get(i));
                    }


                }

            } catch (Exception e) {
                LOGE(TAG, "Network error");
            }

            // Done!
            return movieList;
        }

        /**
         * Called when there is new data to deliver to the client.  The
         * super class will take care of delivering it; the implementation
         * here just adds a little more logic.
         */
        @Override
        public void deliverResult(ArrayList<Movie> movies) {
            if (isReset()) {
                // An async query came in while the loader is stopped.  We
                // don't need the result.
                if (movies != null) {
                    onReleaseResources(movies);
                }
            }
            ArrayList<Movie> oldMovies = mMovies;
            mMovies = movies;

            if (isStarted()) {
                // If the Loader is currently started, we can immediately
                // deliver its results.
                super.deliverResult(movies);
            }

            // At this point we can release the resources associated with
            // 'oldMovies' if needed; now that the new result is delivered we
            // know that it is no longer in use.
            if (oldMovies != null) {
                onReleaseResources(oldMovies);
            }
        }

        /**
         * Handles a request to start the Loader.
         */
        @Override
        protected void onStartLoading() {
            if (mMovies != null) {
                // If we currently have a result available, deliver it
                // immediately.
                deliverResult(mMovies);
            }


            if (takeContentChanged() || mMovies == null) {
                // If the data has changed since the last time it was loaded
                // or is not currently available, start a load.
                forceLoad();
            }
        }

        /**
         * Handles a request to stop the Loader.
         */
        @Override
        protected void onStopLoading() {
            // Attempt to cancel the current load task if possible.
            cancelLoad();
        }

        /**
         * Handles a request to cancel a load.
         */
        @Override
        public void onCanceled(ArrayList<Movie> movies) {
            super.onCanceled(movies);

            // At this point we can release the resources associated with 'movies'
            // if needed.
            onReleaseResources(movies);
        }

        /**
         * Handles a request to completely reset the Loader.
         */
        @Override
        protected void onReset() {
            super.onReset();

            // Ensure the loader is stopped
            onStopLoading();

            // At this point we can release the resources associated with 'movies'
            // if needed.
            if (mMovies != null) {
                onReleaseResources(mMovies);
                mMovies = null;
            }

        }

        /**
         * Helper function to take care of releasing resources associated
         * with an actively loaded data set.
         */
        protected void onReleaseResources(ArrayList<Movie> movies) {
            // For a simple ArrayList<> there is nothing to do.  For something
            // like a Cursor, we would close it here.
        }
    }


    //private static final int TAG_METADATA_TOKEN = 0x4;
    private static final int POPULAR_TOKEN = 0x0;
    private static final int NOW_PLAYING_TOKEN = 0x1;
    private static final int UPCOMING_TOKEN = 0x2;
}
