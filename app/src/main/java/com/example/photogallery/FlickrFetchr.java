package com.example.photogallery;

import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FlickrFetchr {
    private static final String TAG = "FlickrFetchr";
    private static final String API_KEY = "f2b15a9d3db51c6c02c76bdcfc8f207f";

    public byte[] getUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = connection.getInputStream();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException(connection.getResponseMessage() + ": with " + urlSpec);
            }

            int bytesRead = 0;
            byte[] buffer = new byte[1024];
            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }
            out.close();
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    public String getUrlString(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

    public List<GalleryItem> fetchItems(int page) {
        List<GalleryItem> items = new ArrayList<>();
        try {
            String url = Uri.parse("https://api.flickr.com/services/rest/")
                    .buildUpon()
                    .appendQueryParameter("method", "flickr.photos.getRecent")
                    .appendQueryParameter("api_key", API_KEY)
                    .appendQueryParameter("format", "json")
                    .appendQueryParameter("nojsoncallback", "1")
                    .appendQueryParameter("extras", "url_s")
                    .appendQueryParameter("page", Integer.toString(page))
                    .build().toString();
            String jsonString = getUrlString(url);
            Log.i(TAG, "Received JSON: " + jsonString);
            items = parseItems(jsonString);
            return items;
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to fetch items", ioe);
        }

        return items;
    }

    private List<GalleryItem> parseItems(String jsonString) {
        Gson gson = new Gson();
        ApiResponse response = gson.fromJson(jsonString, ApiResponse.class);
        List<GalleryItem> items = response.getPhotos().getPhoto();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getUrl() == null) {
                items.remove(i);
                i--;
            }
        }
        return items;
    }

    private class ApiResponseNested {
        private List<GalleryItem> photo;

        public List<GalleryItem> getPhoto() {
            return photo;
        }

        public void setPhoto(List<GalleryItem> photo) {
            this.photo = photo;
        }
    }

    private class ApiResponse {
        private ApiResponseNested photos;

        public ApiResponseNested getPhotos() {
            return photos;
        }

        public void setPhotos(ApiResponseNested photos) {
            this.photos = photos;
        }
    }
}
