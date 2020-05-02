package com.example.photogallery;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.widget.SearchView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PhotoGalleryFragment extends VisibleFragment {
    private ProgressBar mProgressBar;
    private RecyclerView mPhotoRecyclerView;
    private ThumbnailDownloader<Integer> mThumbnailDownloader;

    private List<GalleryItem> mItems = new ArrayList<>();
    private int mCurrentPage;
    private boolean mLoading;
    private boolean mFreshStart;

    private static final String TAG = "PhotoGalleryFragment";

    private static final String CURRENT_PAGE_KEY = "currentPage";
    private static final String LOADING_KEY = "loading";
    private static final String FRESH_START_KEY = "freshStart";


    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCurrentPage = 1;
        mLoading = true;
        mFreshStart = true;
        if (savedInstanceState != null) {
            mLoading = savedInstanceState.getBoolean(LOADING_KEY);
            mCurrentPage = savedInstanceState.getInt(CURRENT_PAGE_KEY);
            mFreshStart = savedInstanceState.getBoolean(FRESH_START_KEY);
        }
        setRetainInstance(true);
        setHasOptionsMenu(true);
        updateItems();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(new ThumbnailDownloader.ThumbnailDownloadListener<Integer>() {
            @Override
            public void onThumbnailDownloaded(Integer position, Bitmap thumbnail) {
                Objects.requireNonNull(mPhotoRecyclerView.getAdapter()).notifyItemChanged(position);
            }
        });
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = v.findViewById(R.id.photo_recycler_view);
        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int width = mPhotoRecyclerView.getWidth();
                int column_width = 200;
                mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), width / column_width));
            }
        });
        setUpAdapter();
        mProgressBar = v.findViewById(R.id.progress_bar);
        mProgressBar.setIndeterminate(true);
        mProgressBar.setVisibility(View.GONE);
        mPhotoRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (!mPhotoRecyclerView.canScrollVertically(1) && !mLoading && mCurrentPage <= 10) {
                    mLoading = true;
                    mProgressBar.setVisibility(View.VISIBLE);
                    updateItems();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int lastVisibleItem = ((GridLayoutManager) Objects.requireNonNull(mPhotoRecyclerView.getLayoutManager())).findLastVisibleItemPosition();
                int firstVisibleItem = ((GridLayoutManager)mPhotoRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                int beginPosition = Math.max(firstVisibleItem - 10, 0);
                int endPosition = Math.min(lastVisibleItem + 10, mItems.size() - 1);
                for (int i = beginPosition; i <= endPosition; i++) {
                    if (mThumbnailDownloader.getBitmap(mItems.get(i).getUrl()) == null) {
                        mThumbnailDownloader.queueThumbnail(i, mItems.get(i).getUrl());
                    }
                }
            }
        });
        return v;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);
                mCurrentPage = 1;
                mFreshStart = true;
                searchView.clearFocus();
                searchView.onActionViewCollapsed();
                mPhotoRecyclerView.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.VISIBLE);
                updateItems();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                searchView.setQuery(query, false);
            }
        });

        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
        if (isServiceAlarmOn()) {
            toggleItem.setTitle(R.string.stop_polling);
        } else {
            toggleItem.setTitle(R.string.start_polling);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                mCurrentPage = 1;
                mFreshStart = true;
                mPhotoRecyclerView.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.VISIBLE);
                updateItems();
                return true;
            case R.id.menu_item_toggle_polling:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    boolean shouldStartAlarm = !PollServiceNew.hasScheduled(Objects.requireNonNull(getActivity()));
                    if (shouldStartAlarm) {
                        PollServiceNew.schedule(getActivity());
                    } else {
                        PollServiceNew.cancel(getActivity());
                    }
                } else {
                    boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
                    PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
                }
                Objects.requireNonNull(getActivity()).invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute();
    }

    private void setUpAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView mItemImageView;
        private GalleryItem mGalleryItem;

        public PhotoHolder(View itemView) {
            super(itemView);

            mItemImageView = itemView.findViewById(R.id.item_image_view);
            itemView.setOnClickListener(this);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }

        public void bindGalleryItem(GalleryItem galleryItem) {
            mGalleryItem = galleryItem;
        }

        @Override
        public void onClick(View v) {
            Intent i = PhotoPageActivity.newIntent(getActivity(), mGalleryItem.getPhotoPageUri());
            startActivity(i);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {

        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @NonNull
        @Override
        public PhotoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.list_item_gallery, parent, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoHolder holder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            String url = galleryItem.getUrl();
            Bitmap bitmap = mThumbnailDownloader.getBitmap(url);
            if (bitmap == null) {
                Drawable placeholder = getResources().getDrawable(R.drawable.default_image_thumbnail);
                holder.bindDrawable(placeholder);
            } else {
                Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                holder.bindDrawable(drawable);
            }
            holder.bindGalleryItem(galleryItem);
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {

        private String mQuery;

        public FetchItemsTask(String query) {
            mQuery = query;
        }

        @Override
        protected List<GalleryItem> doInBackground(Void... params) {
            if (mQuery == null) {
                return new FlickrFetchr().fetchRecentPhotos(mCurrentPage);
            } else {
                return new FlickrFetchr().searchPhotos(mQuery, mCurrentPage);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            Parcelable prevState = null;
            if (!mFreshStart) {
                prevState = Objects.requireNonNull(mPhotoRecyclerView.getLayoutManager()).onSaveInstanceState();
                mItems.addAll(galleryItems);
            } else {
                mItems = galleryItems;
                mPhotoRecyclerView.setVisibility(View.VISIBLE);
            }
            mCurrentPage++;
            mLoading = false;
            mProgressBar.setVisibility(View.GONE);
            setUpAdapter();
            if (!mFreshStart) {
                Objects.requireNonNull(mPhotoRecyclerView.getLayoutManager()).onRestoreInstanceState(prevState);
            }
            mFreshStart = false;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(LOADING_KEY, mLoading);
        outState.putInt(CURRENT_PAGE_KEY, mCurrentPage);
        outState.putBoolean(FRESH_START_KEY, mFreshStart);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    private boolean isServiceAlarmOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return PollServiceNew.hasScheduled(Objects.requireNonNull(getActivity()));
        } else {
            return PollService.isServiceAlarmOn(getActivity());
        }
    }
}
