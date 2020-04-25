package com.example.photogallery;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PhotoGalleryFragment extends Fragment {
    private ProgressBar mProgressBar;
    private RecyclerView mPhotoRecyclerView;
    private ThumbnailDownloader<Integer> mThumbnailDownloader;

    private List<GalleryItem> mItems = new ArrayList<>();
    private int mCurrentPage;
    private boolean mLoading;

    private static final String TAG = "PhotoGalleryFragment";

    public static final String CURRENT_PAGE_KEY = "currentPage";
    public static final String LOADING_KEY = "loading";

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCurrentPage = 1;
        mLoading = true;
        if (savedInstanceState != null) {
            mLoading = savedInstanceState.getBoolean(LOADING_KEY);
            mCurrentPage = savedInstanceState.getInt(CURRENT_PAGE_KEY);
        }
        setRetainInstance(true);
        new FetchItemsTask().execute();

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
                    new FetchItemsTask().execute();
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

    private void setUpAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);

            mItemImageView = itemView.findViewById(R.id.item_image_view);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
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
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {

        @Override
        protected List<GalleryItem> doInBackground(Void... params) {
            return new FlickrFetchr().fetchItems(mCurrentPage);
        }

        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            Parcelable prevState = Objects.requireNonNull(mPhotoRecyclerView.getLayoutManager()).onSaveInstanceState();
            mItems.addAll(galleryItems);
            mCurrentPage++;
            mLoading = false;
            mProgressBar.setVisibility(View.GONE);
            setUpAdapter();
            mPhotoRecyclerView.getLayoutManager().onRestoreInstanceState(prevState);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(LOADING_KEY, mLoading);
        outState.putInt(CURRENT_PAGE_KEY, mCurrentPage);
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

}
