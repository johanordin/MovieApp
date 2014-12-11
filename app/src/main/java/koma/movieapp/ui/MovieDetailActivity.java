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

import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.request.bitmap.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.uwetrottmann.tmdb.Tmdb;
import com.uwetrottmann.tmdb.entities.Genre;
import com.uwetrottmann.tmdb.entities.Movie;
import com.uwetrottmann.tmdb.services.MoviesService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import koma.movieapp.Config;
import koma.movieapp.R;
import koma.movieapp.ui.widget.CheckableFrameLayout;
import koma.movieapp.ui.widget.ObservableScrollView;
import koma.movieapp.util.ImageLoader;
import koma.movieapp.util.LogUtils;
import koma.movieapp.util.UIUtils;

import static koma.movieapp.util.LogUtils.LOGE;

/**
 * An activity that shows detail information for a session, including session title, abstract,
 * time information, speaker photos and bios, etc.
 */
public class MovieDetailActivity extends BaseActivity implements
        LoaderManager.LoaderCallbacks<Movie>,
        ObservableScrollView.Callbacks {
    private static final String TAG = LogUtils.makeLogTag(MovieDetailActivity.class);

    private static final float PHOTO_ASPECT_RATIO = 1.7777777f;

    public static final String TRANSITION_NAME_PHOTO = "photo";

    private Handler mHandler = new Handler();
    private static final int TIME_HINT_UPDATE_INTERVAL = 10000; // 10 sec

    private int mMovieId;

    private String mTitleString;
    private String mHashTag;
    private String mUrl;

    private boolean mStarred;

    private View mScrollViewChild;
    private TextView mTitle;
    private TextView mMovieRating;
    private TextView mMovieRuntime;


    private ObservableScrollView mScrollView;
    private CheckableFrameLayout mAddScheduleButton;

    private TextView mMovieOverview;
    private ViewGroup mMovieGenresContainer;
    private LinearLayout mMovieGenres;

    private View mHeaderBox;
    private View mDetailsContainer;

    private boolean mSessionCursor = false;
    private boolean mSpeakersCursor = false;
    private boolean mHasSummaryContent = false;

    private ImageLoader mBackdropImageLoader, mNoPlaceholderImageLoader;
    private List<Runnable> mDeferredUiOperations = new ArrayList<Runnable>();

    private StringBuilder mBuffer = new StringBuilder();

    private int mPhotoHeightPixels;
    private int mHeaderHeightPixels;
    private int mAddScheduleButtonHeightPixels;

    private boolean mHasPhoto;
    private View mPhotoViewContainer;
    private ImageView mPhotoView;
    private int mMovieColor;

    private Runnable mTimeHintUpdaterRunnable = null;

    // this set stores the session IDs for which the user has dismissed the
    // "give feedback" card. This information is kept for the duration of the app's execution
    // so that if they say "No, thanks", we don't show the card again for that session while
    // the app is still executing.
    private static HashSet<String> sDismissedFeedbackCard = new HashSet<String>();

    private TextView mSubmitFeedbackView;
    private float mMaxHeaderElevation;
    private float mFABElevation;

    private int mTagColorDotSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //UIUtils.tryTranslateHttpIntent(this);
        boolean shouldBeFloatingWindow = shouldBeFloatingWindow();
        if (shouldBeFloatingWindow) {
            setupFloatingWindow();
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_detail);

        final Toolbar toolbar = getActionBarToolbar();
        toolbar.setNavigationIcon(shouldBeFloatingWindow
                ? R.drawable.ic_ab_close : R.drawable.ic_up);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                toolbar.setTitle("");
            }
        });

        if (savedInstanceState == null) {
            Uri sessionUri = getIntent().getData();
        }

        Bundle extras = getIntent().getExtras();

        if (extras == null) {
            return;
        }

        mMovieId = Integer.parseInt(extras.getString("movieId"));


        mFABElevation = getResources().getDimensionPixelSize(R.dimen.fab_elevation);
        mMaxHeaderElevation = getResources().getDimensionPixelSize(
                R.dimen.session_detail_max_header_elevation);

        mTagColorDotSize = getResources().getDimensionPixelSize(R.dimen.tag_color_dot_size);

        mHandler = new Handler();

        if (mBackdropImageLoader == null) {
            mBackdropImageLoader = new ImageLoader(this, R.drawable.person_image_empty);
        }
        if (mNoPlaceholderImageLoader == null) {
            mNoPlaceholderImageLoader = new ImageLoader(this);
        }

        mScrollView = (ObservableScrollView) findViewById(R.id.scroll_view);
        mScrollView.addCallbacks(this);
        ViewTreeObserver vto = mScrollView.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnGlobalLayoutListener(mGlobalLayoutListener);
        }

        mScrollViewChild = findViewById(R.id.scroll_view_child);
        mScrollViewChild.setVisibility(View.INVISIBLE);

        mDetailsContainer = findViewById(R.id.details_container);
        mHeaderBox = findViewById(R.id.header_movie);
        mTitle = (TextView) findViewById(R.id.movie_title);
        mMovieRating = (TextView) findViewById(R.id.movie_rating);
        mMovieRuntime = (TextView) findViewById(R.id.movie_runtime);
        mPhotoViewContainer = findViewById(R.id.movie_photo_container);
        mPhotoView = (ImageView) findViewById(R.id.movie_backdrop);

        mMovieOverview = (TextView) findViewById(R.id.movie_overview);
        mMovieGenres = (LinearLayout) findViewById(R.id.movie_genre);
        mMovieGenresContainer = (ViewGroup) findViewById(R.id.movie_genres_container);

        mAddScheduleButton = (CheckableFrameLayout) findViewById(R.id.add_schedule_button);


        ViewCompat.setTransitionName(mPhotoView, TRANSITION_NAME_PHOTO);

        LoaderManager manager = getLoaderManager();
        manager.initLoader(mMovieId, null, this);
    }

    @Override
    public Intent getParentActivityIntent() {
        // TODO(mangini): make this Activity navigate up to the right screen depending on how it was launched
        return new Intent(this, PopularMoviesActivity.class);
    }

    private void setupFloatingWindow() {
        // configure this Activity as a floating window, dimming the background
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = getResources().getDimensionPixelSize(R.dimen.session_details_floating_width);
        params.height = getResources().getDimensionPixelSize(R.dimen.session_details_floating_height);
        params.alpha = 1;
        params.dimAmount = 0.4f;
        params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        getWindow().setAttributes(params);
    }

    private boolean shouldBeFloatingWindow() {
        Resources.Theme theme = getTheme();
        TypedValue floatingWindowFlag = new TypedValue();
        if (theme == null || !theme.resolveAttribute(R.attr.isFloatingWindow, floatingWindowFlag, true)) {
            // isFloatingWindow flag is not defined in theme
            return false;
        }
        return (floatingWindowFlag.data != 0);
    }

    private void recomputePhotoAndScrollingMetrics() {
        mHeaderHeightPixels = mHeaderBox.getHeight();

        mPhotoHeightPixels = 0;
        if (mHasPhoto) {
            mPhotoHeightPixels = (int) (mPhotoView.getWidth() / PHOTO_ASPECT_RATIO);
            mPhotoHeightPixels = Math.min(mPhotoHeightPixels, mScrollView.getHeight() * 2 / 3);
        }

        ViewGroup.LayoutParams lp;
        lp = mPhotoViewContainer.getLayoutParams();
        if (lp.height != mPhotoHeightPixels) {
            lp.height = mPhotoHeightPixels;
            mPhotoViewContainer.setLayoutParams(lp);
        }

        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams)
                mDetailsContainer.getLayoutParams();
        if (mlp.topMargin != mHeaderHeightPixels + mPhotoHeightPixels) {
            mlp.topMargin = mHeaderHeightPixels + mPhotoHeightPixels;
            mDetailsContainer.setLayoutParams(mlp);
        }

        onScrollChanged(0, 0); // trigger scroll handling
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mScrollView == null) {
            return;
        }

        ViewTreeObserver vto = mScrollView.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.removeGlobalOnLayoutListener(mGlobalLayoutListener);
        }
    }

    private ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener
            = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            mAddScheduleButtonHeightPixels = mAddScheduleButton.getHeight();
            recomputePhotoAndScrollingMetrics();
        }
    };

    @Override
    public void onScrollChanged(int deltaX, int deltaY) {
        // Reposition the header bar -- it's normally anchored to the top of the content,
        // but locks to the top of the screen on scroll
        int scrollY = mScrollView.getScrollY();

        float newTop = Math.max(mPhotoHeightPixels, scrollY);
        mHeaderBox.setTranslationY(newTop);
        mAddScheduleButton.setTranslationY(newTop + mHeaderHeightPixels
                - mAddScheduleButtonHeightPixels / 2);

        float gapFillProgress = 1;
        if (mPhotoHeightPixels != 0) {
            gapFillProgress = Math.min(Math.max(UIUtils.getProgress(scrollY,
                    0,
                    mPhotoHeightPixels), 0), 1);
        }

        ViewCompat.setElevation(mHeaderBox, gapFillProgress * mMaxHeaderElevation);
        ViewCompat.setElevation(mAddScheduleButton, gapFillProgress * mMaxHeaderElevation
                + mFABElevation);

        // Move background photo (parallax effect)
        mPhotoViewContainer.setTranslationY(scrollY * 0.5f);
    }

    @Override
    public void onResume() {
        super.onResume();
        //updatePlusOneButton();
        if (mTimeHintUpdaterRunnable != null) {
            mHandler.postDelayed(mTimeHintUpdaterRunnable, TIME_HINT_UPDATE_INTERVAL);
        }

        // Refresh whether or not feedback has been submitted
        getLoaderManager().restartLoader(mMovieId, null, this);
    }

    @Override
    public void onStop() {
        super.onStop();

    }


    @Override
    public void onPause() {
        super.onPause();
        if (mTimeHintUpdaterRunnable != null) {
            mHandler.removeCallbacks(mTimeHintUpdaterRunnable);
        }
    }


//    private void showStarredDeferred(final boolean starred, final boolean allowAnimate) {
//        mDeferredUiOperations.add(new Runnable() {
//            @Override
//            public void run() {
//                showStarred(starred, allowAnimate);
//            }
//        });
//        //tryExecuteDeferredUiOperations();
//    }

    private void showStarred(boolean starred, boolean allowAnimate) {
        mStarred = starred;

        mAddScheduleButton.setChecked(mStarred, allowAnimate);

        ImageView iconView = (ImageView) mAddScheduleButton.findViewById(R.id.add_schedule_icon);
        getLUtils().setOrAnimatePlusCheckIcon(iconView, starred, allowAnimate);
        mAddScheduleButton.setContentDescription(getString(starred
                ? R.string.remove_from_schedule_desc
                : R.string.add_to_schedule_desc));
    }


//    private void updateEmptyView() {
//        findViewById(android.R.id.empty).setVisibility(
//                (mSpeakersCursor && mSessionCursor && !mHasSummaryContent)
//                        ? View.VISIBLE
//                        : View.GONE);
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.session_detail, menu);
//        mShareMenuItem = menu.findItem(R.id.menu_share);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            default:
                break;
        }
        return false;
    }

    @Override
    public Loader<Movie> onCreateLoader(int id, Bundle data) {
        return new MovieLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<Movie> loader, Movie movie) {


        mTitleString = movie.title;
        mMovieColor = getResources().getColor(R.color.default_movie_color);

        mHeaderBox.setBackgroundColor(mMovieColor);
        getLUtils().setStatusBarColor(UIUtils.scaleColor(mMovieColor, 0.8f, false));

        final String movieRating = movie.vote_average.toString();
        final String movieRuntime = movie.runtime.toString();

        final String movieOverview = movie.overview;

        // Movie title
        mTitle.setText(mTitleString);

        // Movie rating
        if (movieRating.isEmpty()) {
            mMovieRating.setVisibility(View.GONE);
        } else {
            mMovieRating.setText(movieRating + "/10");
        }

        // Movie runtime
        if (movieRuntime.isEmpty()) {
            mMovieRuntime.setVisibility(View.GONE);
        } else {
            mMovieRuntime.setText(movieRuntime + " " + getResources().getString(R.string.minutes));
        }


        if (movieOverview.isEmpty()) {
            mMovieOverview.setVisibility(View.GONE);
        } else {
            mMovieOverview.setText(movieOverview);
        }

        final String backdropUrl = Config.TMDB_IMAGE_BASE_URL + Config.TMDB_IMAGE_SIZE + movie.backdrop_path;

        if (!TextUtils.isEmpty(backdropUrl)) {
            mHasPhoto = true;
            mNoPlaceholderImageLoader.loadImage(backdropUrl, mPhotoView, new RequestListener<String>() {
                @Override
                public void onException(Exception e, String url, Target target) {
                    mHasPhoto = false;
                    recomputePhotoAndScrollingMetrics();
                }

                @Override
                public void onImageReady(String url, Target target, boolean b, boolean b2) {
                    // Trigger image transition
                    recomputePhotoAndScrollingMetrics();
                }
            });
            recomputePhotoAndScrollingMetrics();
        } else {
            mHasPhoto = false;
            recomputePhotoAndScrollingMetrics();
        }


        List<Genre> genresList = movie.genres;

        if (!genresList.isEmpty()) {
            mMovieGenresContainer.setVisibility(View.VISIBLE);
            mMovieGenres.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);

            for (Genre genre : genresList) {
                TextView chipView = (TextView) inflater.inflate(
                        R.layout.include_session_tag_chip, mMovieGenres, false);
                chipView.setText(genre.name);
                mMovieGenres.addView(chipView);
            }
        } else {
            mMovieGenresContainer.setVisibility(View.GONE);
        }

        //updateEmptyView();

        mAddScheduleButton.setVisibility(View.VISIBLE);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                onScrollChanged(0, 0); // trigger scroll handling
                mScrollViewChild.setVisibility(View.VISIBLE);
                //mMovieOverview.setTextIsSelectable(true);
            }
        });

//        mTimeHintUpdaterRunnable = new Runnable() {
//            @Override
//            public void run() {
//                updateTimeBasedUi();
//                mHandler.postDelayed(mTimeHintUpdaterRunnable, TIME_HINT_UPDATE_INTERVAL);
//            }
//        };
        //mHandler.postDelayed(mTimeHintUpdaterRunnable, TIME_HINT_UPDATE_INTERVAL);


    }

    @Override
    public void onLoaderReset(Loader<Movie> loader) {
    }


    private static class MovieLoader extends AsyncTaskLoader<Movie> {

        Movie mMovie;
        Tmdb tmdb;

        public MovieLoader(Context context) {
            super(context);

            tmdb = new Tmdb();
            tmdb.setApiKey(Config.TMDB_API_KEY);
        }

        /**
         * This is where the bulk of our work is done.  This function is
         * called in a background thread and should generate a new set of
         * data to be published by the loader.
         */
        @Override
        public Movie loadInBackground() {

            MoviesService moviesService = null;
            Movie movie = null;

            try {
                moviesService = tmdb.moviesService();
                movie = moviesService.summary(this.getId());

            } catch (Exception e) {
                LOGE(TAG, "Network error");
            }

            // Done!
            return movie;
        }

        /**
         * Called when there is new data to deliver to the client.  The
         * super class will take care of delivering it; the implementation
         * here just adds a little more logic.
         */
        @Override
        public void deliverResult(Movie movie) {
            if (isReset()) {
                // An async query came in while the loader is stopped.  We
                // don't need the result.
                if (movie != null) {
                    onReleaseResources(movie);
                }
            }
            Movie oldMovie = mMovie;
            mMovie = movie;

            if (isStarted()) {
                // If the Loader is currently started, we can immediately
                // deliver its results.
                super.deliverResult(movie);
            }

            // At this point we can release the resources associated with
            // 'oldMovies' if needed; now that the new result is delivered we
            // know that it is no longer in use.
            if (oldMovie != null) {
                onReleaseResources(oldMovie);
            }
        }

        /**
         * Handles a request to start the Loader.
         */
        @Override
        protected void onStartLoading() {
            if (mMovie != null) {
                // If we currently have a result available, deliver it
                // immediately.
                deliverResult(mMovie);
            }


            if (takeContentChanged() || mMovie == null) {
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
        public void onCanceled(Movie movie) {
            super.onCanceled(movie);

            // At this point we can release the resources associated with 'movie'
            // if needed.
            onReleaseResources(movie);
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
            if (mMovie != null) {
                onReleaseResources(mMovie);
                mMovie = null;
            }

        }

        /**
         * Helper function to take care of releasing resources associated
         * with an actively loaded data set.
         */
        protected void onReleaseResources(Movie movie) {
            // For a simple ArrayList<> there is nothing to do.  For something
            // like a Cursor, we would close it here.
        }
    }


}
