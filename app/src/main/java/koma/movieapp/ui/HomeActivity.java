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

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import koma.movieapp.R;
import koma.movieapp.ui.widget.CollectionView;
import koma.movieapp.ui.widget.DrawShadowFrameLayout;
import koma.movieapp.util.UIUtils;

import static koma.movieapp.util.LogUtils.makeLogTag;

public class HomeActivity extends BaseActivity implements MoviesFragment.Callbacks {
    private static final String TAG = makeLogTag(HomeActivity.class);

    // How is this Activity being used?
    private static final int MODE_HOME = 0; // as top-level "Home" screen
    private static final int MODE_TIME_FIT = 1; // showing sessions that fit in a time interval

    private static final String STATE_FILTER_0 = "STATE_FILTER_0";
    private static final String STATE_FILTER_1 = "STATE_FILTER_1";
    private static final String STATE_FILTER_2 = "STATE_FILTER_2";


    private int mMode = MODE_HOME;

    private final static String SCREEN_LABEL = "Explore";

    private boolean mSpinnerConfigured = false;

    // filter tags that are currently selected
    private String[] mFilterTags = {"", "", ""};

    // filter tags that we have to restore (as a result of Activity recreation)
    private String[] mFilterTagsToRestore = {null, null, null};

    private MoviesFragment mMoviesFrag = null;

    private DrawShadowFrameLayout mDrawShadowFrameLayout;
    private View mButterBar;

    // time when the user last clicked "refresh" from the stale data butter bar
    private long mLastDataStaleUserActionTime = 0L;
    private int mHeaderColor = 0; // 0 means not customized


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);
        Log.d("HomeActivity", "HomeActivity.onCreate() — says ");

        Toolbar toolbar = getActionBarToolbar();

        overridePendingTransition(0, 0);

        if (mMode == MODE_HOME) {
            // no title (to make more room for navigation and actions)
            // unless Nav Drawer opens
            toolbar.setTitle(null);
        }

        mButterBar = findViewById(R.id.butter_bar);
        mDrawShadowFrameLayout = (DrawShadowFrameLayout) findViewById(R.id.main_content);
        registerHideableHeaderView(mButterBar);


    }

    @Override
    public void onResume() {
        super.onResume();
        //checkShowStaleDataButterBar();
    }

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        if (mMoviesFrag != null) {
            return mMoviesFrag.canCollectionViewScrollUp();
        }
        return super.canSwipeRefreshChildScrollUp();
    }

//    private void checkShowStaleDataButterBar() {
//        final boolean showingFilters = findViewById(R.id.filters_box) != null
//                && findViewById(R.id.filters_box).getVisibility() == View.VISIBLE;
//        final long now = UIUtils.getCurrentTime(this);
//        final boolean inSnooze = (now - mLastDataStaleUserActionTime < Config.STALE_DATA_WARNING_SNOOZE);
//        final long staleTime = now - PrefUtils.getLastSyncSucceededTime(this);
//        final long staleThreshold = Config.STALE_DATA_THRESHOLD;
//        final boolean isStale = (staleTime >= staleThreshold);
//        //final boolean bootstrapDone = PrefUtils.isDataBootstrapDone(this);
//        //final boolean mustShowBar = bootstrapDone && isStale && !inSnooze && !showingFilters;
//        final boolean mustShowBar = isStale && !inSnooze && !showingFilters;
//
////        if (!mustShowBar) {
////            mButterBar.setVisibility(View.GONE);
////        } else {
////            UIUtils.setUpButterBar(mButterBar, getString(R.string.data_stale_warning),
////                    getString(R.string.description_refresh), new View.OnClickListener() {
////                        @Override
////                        public void onClick(View v) {
////                            mButterBar.setVisibility(View.GONE);
////                            updateFragContentTopClearance();
////                            mLastDataStaleUserActionTime = UIUtils.getCurrentTime(
////                                    HomeActivity.this);
////                            //requestDataRefresh();
////                        }
////                    }
////            );
////        }
//        updateFragContentTopClearance();
//    }

    @Override
    protected int getSelfNavDrawerItem() {
        // we only have a nav drawer if we are in top-level Explore mode.
        return mMode == MODE_HOME ? NAVDRAWER_ITEM_HOME : NAVDRAWER_ITEM_INVALID;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        CollectionView collectionView = (CollectionView) findViewById(R.id.movies_collection_view);
        if (collectionView != null) {
            enableActionBarAutoHide(collectionView);
        }

        mMoviesFrag = (MoviesFragment) getFragmentManager().findFragmentById(
                R.id.movies_fragment);
        if (mMoviesFrag != null && savedInstanceState == null) {
            Bundle args = intentToFragmentArguments(getIntent());
            mMoviesFrag.reloadFromArguments(args);
        }

        registerHideableHeaderView(findViewById(R.id.headerbar));
    }


//    private void trySetUpActionBarSpinner() {
//        Toolbar toolbar = getActionBarToolbar();
//        if (mMode != MODE_HOME || mSpinnerConfigured || mTagMetadata == null || toolbar == null) {
//            // already done it, or not ready yet, or don't need to do
//            LOGD(TAG, "Not configuring Action Bar spinner.");
//            return;
//        }
//
//        LOGD(TAG, "Configuring Action Bar spinner.");
//        mSpinnerConfigured = true;
//        mTopLevelSpinnerAdapter.clear();
//        //mTopLevelSpinnerAdapter.addItem("", getString(R.string.all_sessions), false, 0);
//
//        int itemToSelect = -1;
//
//        mFilterTagsToRestore[0] = null;
//
//        View spinnerContainer = LayoutInflater.from(this).inflate(R.layout.actionbar_spinner,
//                toolbar, false);
//        ActionBar.LayoutParams lp = new ActionBar.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
//        toolbar.addView(spinnerContainer, lp);
//
//        Spinner spinner = (Spinner) spinnerContainer.findViewById(R.id.actionbar_spinner);
//        spinner.setAdapter(mTopLevelSpinnerAdapter);
//        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> spinner, View view, int position, long itemId) {
//                onTopLevelTagSelected(mTopLevelSpinnerAdapter.getTag(position));
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> adapterView) {
//            }
//        });
//        if (itemToSelect >= 0) {
//            LOGD(TAG, "Restoring item selection to primary spinner: " + itemToSelect);
//            spinner.setSelection(itemToSelect);
//        }
//
//        updateHeaderColor();
//        showSecondaryFilters();
//    }

    private void updateHeaderColor() {
        mHeaderColor = 0;

        findViewById(R.id.headerbar).setBackgroundColor(
                mHeaderColor == 0
                        ? getResources().getColor(R.color.theme_primary)
                        : mHeaderColor);
        setNormalStatusBarColor(
                mHeaderColor == 0
                        ? getThemedStatusBarColor()
                        : UIUtils.scaleColor(mHeaderColor, 0.8f, false));
    }


    // Updates the Sessions fragment content top clearance to take our chrome into account
    private void updateFragContentTopClearance() {
        MoviesFragment frag = (MoviesFragment) getFragmentManager().findFragmentById(
                R.id.movies_fragment);
        if (frag == null) {
            return;
        }

        View filtersBox = findViewById(R.id.filters_box);

        final boolean filterBoxVisible = filtersBox != null
                && filtersBox.getVisibility() == View.VISIBLE;
        final boolean butterBarVisible = mButterBar != null
                && mButterBar.getVisibility() == View.VISIBLE;

        int actionBarClearance = UIUtils.calculateActionBarSize(this);
        int butterBarClearance = butterBarVisible
                ? getResources().getDimensionPixelSize(R.dimen.butter_bar_height) : 0;
        int filterBoxClearance = filterBoxVisible
                ? getResources().getDimensionPixelSize(R.dimen.filterbar_height) : 0;
        int secondaryClearance = butterBarClearance > filterBoxClearance ? butterBarClearance :
                filterBoxClearance;
        int gridPadding = getResources().getDimensionPixelSize(R.dimen.explore_grid_padding);

        setProgressBarTopWhenActionBarShown(actionBarClearance + secondaryClearance);
        mDrawShadowFrameLayout.setShadowTopOffset(actionBarClearance + secondaryClearance);
        frag.setContentTopClearance(actionBarClearance + secondaryClearance + gridPadding);
    }


    @Override
    protected void onActionBarAutoShowOrHide(boolean shown) {
        super.onActionBarAutoShowOrHide(shown);
        mDrawShadowFrameLayout.setShadowVisible(shown, shown);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_search:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // TODO
    @Override
    public void onSessionSelected(String movieId, View clickedView) {
//        getLUtils().startActivityWithTransition(new Intent(Intent.ACTION_VIEW,
//                        ScheduleContract.Sessions.buildSessionUri(movieId)),
//                clickedView,
//                MovieDetailActivity.TRANSITION_NAME_PHOTO);

        Intent intent = new Intent(this, MovieDetailActivity.class);
        intent.putExtra("movieId", movieId);

        getLUtils().startActivityWithTransition(intent,clickedView, MovieDetailActivity.TRANSITION_NAME_PHOTO);
//
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_FILTER_0, mFilterTags[0]);
        outState.putString(STATE_FILTER_1, mFilterTags[1]);
        outState.putString(STATE_FILTER_2, mFilterTags[2]);
    }


}
