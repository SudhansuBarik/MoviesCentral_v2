package io.github.sudhansubarik.moviescentral.activities;

/*
 * References
 *
 * https://www.androidhive.info/2016/05/android-working-with-card-view-and-recycler-view/
 * https://abhiandroid.com/materialdesign/recyclerview-gridview.html
 * https://stackoverflow.com/a/40587169
 * https://stackoverflow.com/questions/40118264/retrofit-library-with-api-the-movie-db
 * https://stackoverflow.com/questions/41632590/clicking-cardview-instead-of-clicking-items-inside
 */

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.sudhansubarik.moviescentral.BuildConfig;
import io.github.sudhansubarik.moviescentral.R;
import io.github.sudhansubarik.moviescentral.adapters.MoviesAdapter;
import io.github.sudhansubarik.moviescentral.models.Movie;
import io.github.sudhansubarik.moviescentral.models.MoviesList;
import io.github.sudhansubarik.moviescentral.models.MoviesViewModel;
import io.github.sudhansubarik.moviescentral.room.DbMovies;
import io.github.sudhansubarik.moviescentral.utils.Api;
import io.github.sudhansubarik.moviescentral.utils.MoviesApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static String API_KEY;

    Spinner filterSpinner;
    RecyclerView recyclerView;
    ProgressBar progressBar;
    public static final String LIFECYCLE_CALLBACK_MOVIE_LIST = "movie_list";
    Boolean doubleBackToExitPressedOnce = false;
    Boolean isScrolling = false;
    int currentItems, totalItems, scrollOutItems, movieRequestType = 1;
    GridLayoutManager manager;
    List<Movie> movieList = new ArrayList<>();
    List<DbMovies> dbMoviesList = new ArrayList<>();
    MoviesAdapter moviesAdapter;
    MoviesViewModel moviesViewModel;
    private Parcelable recyclerViewState;
    ArrayAdapter<String> dataAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        API_KEY = BuildConfig.ApiKey;
        if (API_KEY.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Invalid API Key", Toast.LENGTH_LONG).show();
            return;
        }

        moviesViewModel = ViewModelProviders.of(this).get(MoviesViewModel.class);

        filterSpinner = findViewById(R.id.filter_spinner);
        recyclerView = findViewById(R.id.home_recyclerView);
        progressBar = findViewById(R.id.activity_main_progressBar);

        manager = new GridLayoutManager(this, 2);
        recyclerView.setLayoutManager(manager);
        // Designate all items in the grid to have the same size
        recyclerView.setHasFixedSize(true);

        // Spinner dropdown elements
        String[] filter = {"Popular", "Top Rated", "Favorites"};
        // Creating adapter for spinner
        dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filter);
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // attaching data adapter to spinner
        filterSpinner.setAdapter(dataAdapter);
        // Set what would happen when an option is selected in the spinner
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                switch (position) {
                    case 0:
                        movieRequestType = 1;
                        loadMovies();
                        break;
                    case 1:
                        movieRequestType = 2;
                        loadMovies();
                        break;
                    case 2:
                        movieRequestType = 3;
                        loadMovies();
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (movieRequestType != 3) {
                    if (newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                        isScrolling = true;
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (movieRequestType != 3) {
                    currentItems = manager.getChildCount();

                    totalItems = manager.getItemCount();
                    scrollOutItems = manager.findFirstVisibleItemPosition();
                }
            }
        });

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(LIFECYCLE_CALLBACK_MOVIE_LIST)) {
                movieList = savedInstanceState.getParcelableArrayList(LIFECYCLE_CALLBACK_MOVIE_LIST);
                manager.onRestoreInstanceState(recyclerViewState);
            }
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                // db favourite
                moviesViewModel.getAllMovies().observe(MainActivity.this, new Observer<List<DbMovies>>() {
                    @Override
                    public void onChanged(@Nullable List<DbMovies> movies) {
                        dbMoviesList = movies;
                        if (dbMoviesList.size() != 0 && movieRequestType == 3) {
                            Log.d(TAG, ">>>>>>>>>>\n\n>>>" + dbMoviesList.size());
                            loadMovies();
                        } else if (dbMoviesList.size() == 0 && movieRequestType == 3) {
                            if (progressBar != null) {
                                progressBar.setVisibility(View.GONE);
                            }
                            movies.clear();
                            movieRequestType = 1;
                            loadMovies();
                        }
                    }
                });
            }

        }).start();

        loadMovies();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        recyclerViewState = manager.onSaveInstanceState();
        outState.putParcelableArrayList(LIFECYCLE_CALLBACK_MOVIE_LIST, (ArrayList<? extends Parcelable>) movieList);
    }

    public void loadMovies() {
        final int response = movieRequestType;
        final MoviesApiService apiService = Api.getClient().create(MoviesApiService.class);

        if (isOnline()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBar.setVisibility(View.VISIBLE);
                    if (response == 3) {
                        if (dbMoviesList.size() > 0) {
                            movieList.clear();
                            for (int i = 0; i < dbMoviesList.size(); i++) {
                                Log.d(TAG, "For " + dbMoviesList.get(i).getMovieId());
                                Call<Movie> call = apiService.getMovieDetails(dbMoviesList.get(i).getMovieId(), API_KEY);
                                call.enqueue(new Callback<Movie>() {
                                    @Override
                                    public void onResponse(Call<Movie> call, Response<Movie> response) {

                                        Movie a = response.body();

                                        Log.d(TAG, a.getTitle());
                                        movieList.add(a);
                                        Log.d(TAG, movieList.get(0).getTitle());
                                        if (dbMoviesList.size() == dbMoviesList.size()) {
                                            Log.e(TAG, movieList.size() + " size");
                                            moviesAdapter = new MoviesAdapter(getApplicationContext(), movieList);
                                            recyclerView.setAdapter(moviesAdapter);
                                        }
                                        if (progressBar != null) {
                                            progressBar.setVisibility(View.GONE);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<Movie> call, Throwable t) {
                                        // Log error here since request failed
                                        Log.e(TAG, t.toString());
                                    }
                                });
                            }
                        } else {
                            if (progressBar != null) {
                                progressBar.setVisibility(View.GONE);
                            }
                            recyclerView.setAdapter(null);
//                            filterSpinner.setSelection(dataAdapter.getPosition("Popular"));
                            Toast.makeText(MainActivity.this, "No Favourite movies", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Call<MoviesList> call = apiService.getPopularMovies(API_KEY);
                        switch (response) {
                            case 1:
                                call = apiService.getPopularMovies(API_KEY);
                                break;
                            case 2:
                                call = apiService.getTopRatedMovies(API_KEY);
                                break;
                        }
                        // MoviesList Callback
                        call.enqueue(new Callback<MoviesList>() {
                            @Override
                            public void onResponse(Call<MoviesList> call, Response<MoviesList> response) {
                                List<Movie> a = response.body().getResults();
                                moviesAdapter = new MoviesAdapter(getApplicationContext(), a);
                                recyclerView.setAdapter(moviesAdapter);
                                moviesAdapter.notifyDataSetChanged();
                                if (progressBar != null) {
                                    progressBar.setVisibility(View.GONE);
                                }
                            }

                            @Override
                            public void onFailure(Call<MoviesList> call, Throwable t) {
                                // Log error here since request failed
                                Log.e(TAG, t.toString());
                            }
                        });
                    }
                }
            });
        } else {
            Toast.makeText(this, "Please check your Internet Connection and try again!", Toast.LENGTH_LONG).show();
        }
    }

    // Double Back Press to Exit
    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Click Back again to Exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    /*
        Network changing:
            https://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-times-out
     */
    public boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = null;
        if (cm != null) {
            netInfo = cm.getActiveNetworkInfo();
        }
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }
}
