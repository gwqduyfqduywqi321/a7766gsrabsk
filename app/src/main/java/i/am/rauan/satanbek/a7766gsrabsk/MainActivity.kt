@file:Suppress("IMPLICIT_CAST_TO_ANY", "DEPRECATION")

package i.am.rauan.satanbek.a7766gsrabsk

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.*
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatDelegate
import android.support.v7.content.res.AppCompatResources
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem


@SuppressLint("ByteOrderMark")
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    var TAG: String = "main"

    //Create placeholder for user's consent to record_audio permission.
    //This will be used in handling callback
    private val MY_PERMISSIONS_RECORD_AUDIO: Int = 1

    private lateinit var runnable: Runnable
    private var handler: Handler = Handler()
    private var storage: FirebaseStorage? = null

    private var sharedStorage: Storage? = null

    private var player: MediaPlayerService? = null
    private var serviceBound = false
    private var updateUIReciver: BroadcastReceiver? = null

    private var songs: ArrayList<Song> = ArrayList()

    private lateinit var drawer: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var linearLayoutManager: LinearLayoutManager
    private var recyclerAdapter: RecyclerAdapter? = null

    //AudioPlayer notification ID
    val NOTIFICATION_ID = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

        toggle = ActionBarDrawerToggle(this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        toggle.isDrawerIndicatorEnabled = false
        toggle.setToolbarNavigationClickListener {
            drawer_layout.openDrawer(GravityCompat.START)
        }
        toggle.setHomeAsUpIndicator(R.drawable.ic_humburger)

        nav_view.setNavigationItemSelectedListener(this)
        nav_view.itemIconTintList = null
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                // Set NavView bg
                nav_view.background = AppCompatResources.getDrawable(this, R.drawable.ic_nav_bg_vector)
            } catch (ex: Exception) {
                Log.d(TAG, "Unable to set vector background to NavigationView, API Level = ${Build.VERSION.SDK_INT}")
            }
        }

        // Init storage
        sharedStorage = Storage(this)
        loadSongs()

        initUI()
        requestAudioPermissions(false)
        initMediaService()
    }

    override fun onRestart() {
        super.onRestart()

        if (recyclerAdapter != null) {
            recyclerAdapter?.play()
        }

        var notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
    // Первоначальная настройка UI элементов.
    private fun initUI() {
        setColorMode()

        mediaPlayerContainer.visibility = View.GONE

        // Обнавляем UI элементы, в зависимости от recyclerView, Service
        val updateUIFilter = IntentFilter()
        updateUIFilter.addAction(sharedStorage?.updateUIReceiverAction)
        updateUIReciver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                if (intent.getStringExtra("tv_duration") != "") {
                    Log.d(TAG, "onReceive tv_duration = ${intent.getStringExtra("tv_duration")}")
                    tv_duration.text = intent.getStringExtra("tv_duration")
                }

                if(intent.getBooleanExtra("media_playing", false)) {
                    activateVisualizer()
                    activateSeekBar()
                    showPauseButton()
                }

                if (intent.getBooleanExtra("showPlayButton", false)) {
                    showPlayButton()
                }
                if (intent.getBooleanExtra("showPauseButton", false)) {
                    showPauseButton()
                }

                if(intent.getBooleanExtra("seek_bar_progress", false)) {
                    seek_bar.progress = intent.getIntExtra("seek_bar_progress_value", 0)
                }
                if(intent.getBooleanExtra("player_stop", false)) {
                    handler.removeCallbacks(runnable)
                }

                // PLAY
                if (intent.getBooleanExtra(sharedStorage?.updateUIPlay, false)) {
                    mediaPlayerContainer.visibility = View.VISIBLE

                    val broadcastIntent = Intent(sharedStorage!!.playNewSongReceiverAction)
                    broadcastIntent.putExtra("play", true)
                    sendBroadcast(broadcastIntent)

                    activateVisualizer()
                    activateSeekBar()
                    showPauseButton()

                    recyclerAdapter?.play()
                    sharedStorage?.setPause(false)
                }

                // PAUSE
                if (intent.getBooleanExtra(sharedStorage?.updateUIPause, false)) {
                    Pause()
                }

                // RESUME
                if (intent.getBooleanExtra(sharedStorage?.updateUIResume, false)) {

                }

                // STOP
                if (intent.getBooleanExtra(sharedStorage?.updateUIStop, false)) {

                }

                // NEXT
                if (intent.getBooleanExtra(sharedStorage?.updateUISkipToNext, false)) {
                    Next()
                }

                // PREVIOUS
                if (intent.getBooleanExtra(sharedStorage?.updateUISkipToPrevious, false)) {
                    Previous()
                }

                // PLAY Update just UI
                if (intent.getBooleanExtra(sharedStorage?.updateUIPlayUpdate, false)) {
                    showPauseButton()
                    activateSeekBar()
                    activateVisualizer()

                    recyclerAdapter?.play()
                }

                // PAUSE Update just UI
                if (intent.getBooleanExtra(sharedStorage?.updateUIPauseUpdate, false)) {
                    showPlayButton()

                    recyclerAdapter?.pause()
                }
            }
        }

        registerReceiver(updateUIReciver, updateUIFilter)

        // PLAY
        playBtn.setOnClickListener {
            Play()
        }

        // PAUSE
        pauseBtn.setOnClickListener{
            Pause()
        }

        // NEXT
        nextBtn.setOnClickListener {
            Next()
        }

        // PREVIOUS
        prevBtn.setOnClickListener {
            Previous()
        }

        // Seek bar change listener
        seek_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (b) {
                    player?.seekTo(i * 1000)
                    sharedStorage!!.setResumePosition(i)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        refreshBtn.setOnClickListener{
            showRefreshOne()
        }
        refreshOneBtn.setOnClickListener{
            showRefresh()
        }
        shuffleBtn.setOnClickListener{
            showShuffleActive()
        }
        shuffleBtnActive.setOnClickListener{
            showShuffle()
        }


        when(sharedStorage!!.getRefreshMode()) {
            0 -> showRefresh()
            1 -> showRefreshOne()
        }

        when(sharedStorage!!.getShuffleMode()) {
            0 -> showShuffle()
            1 -> showShuffleActive()
        }

//        createFolder("GaniMatebayev")

    }

    // Set color mode to DARK or LIGHT
    private fun setColorMode() {
        when(sharedStorage?.getColorMode()) {
            sharedStorage?.DARK_MODE -> {
                MainContainer.setBackgroundColor(resources.getColor(R.color.dark_blue))

                mediaPlayerContainer.background = AppCompatResources.getDrawable(this, R.drawable.media_player_bg)
                pauseBtn.background = AppCompatResources.getDrawable(this, R.drawable.fol_verctor)
                toolbar.background = AppCompatResources.getDrawable(this, R.drawable.toolbar_background)

                refreshBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_refresh))
                refreshOneBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_refresh_one))

                prevBtn.background = AppCompatResources.getDrawable(this, R.drawable.rounded_btn)
                prevBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_prev))

                playBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_play))
                pauseBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_pause))

                nextBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_next))
                nextBtn.background = AppCompatResources.getDrawable(this, R.drawable.rounded_btn)

                shuffleBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_shuffle))
                shuffleBtnActive.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_shuffle_active))

            }

            sharedStorage?.LIGHT_MODE -> {
                MainContainer.setBackgroundColor(resources.getColor(R.color.invert_dark_blue))

                mediaPlayerContainer.background = AppCompatResources.getDrawable(this, R.drawable.invert_media_player_bg)
                pauseBtn.background = AppCompatResources.getDrawable(this, R.drawable.fol_verctor_invert)
                toolbar.background = AppCompatResources.getDrawable(this, R.drawable.invert_toolbar_background)

                refreshBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_refresh_invert))
                refreshOneBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_refresh_one_invert))

                prevBtn.background = AppCompatResources.getDrawable(this, R.drawable.rounded_btn_invert)
                prevBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_prev_invert))

                playBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_play))
                pauseBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_pause_invert))

                nextBtn.background = AppCompatResources.getDrawable(this, R.drawable.rounded_btn_invert)
                nextBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_next_invert))

                shuffleBtn.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_shuffle_invert))
                shuffleBtnActive.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_shuffle_active_invert))

//                mVisualizer.setBackgroundColor(resources.getColor(R.color.dark_blue))
            }
        }

        if (recyclerAdapter != null) {
            recyclerAdapter?.setColorModeForHolders()
        } else {
            loadSongs()
            recyclerAdapter?.setColorModeForHolders()
        }
    }

    private fun showPauseButton() {
        pauseBtn.visibility = View.VISIBLE
        pauseBtn.isEnabled = true

        playBtn.visibility = View.INVISIBLE
        playBtn.isEnabled = false
    }

    private fun showPlayButton() {
        pauseBtn.visibility = View.INVISIBLE
        pauseBtn.isEnabled = false

        playBtn.visibility = View.VISIBLE
        playBtn.isEnabled = true
    }

    private fun showShuffle() {
        shuffleBtn.visibility = View.VISIBLE
        shuffleBtn.isEnabled = true
        sharedStorage!!.setSuffleMode(0)

        shuffleBtnActive.visibility = View.GONE
        shuffleBtnActive.isEnabled = false
    }

    private fun showShuffleActive() {
        shuffleBtnActive.visibility = View.VISIBLE
        shuffleBtnActive.isEnabled = true
        sharedStorage!!.setSuffleMode(1)

        shuffleBtn.visibility = View.GONE
        shuffleBtn.isEnabled = false
    }

    private fun showRefresh() {
        refreshBtn.visibility = View.VISIBLE
        refreshBtn.isEnabled = true
        sharedStorage!!.setRefreshMode(0)


        refreshOneBtn.visibility = View.GONE
        refreshOneBtn.isEnabled = false
    }

    private fun showRefreshOne() {
        refreshBtn.visibility = View.GONE
        refreshBtn.isEnabled = false


        refreshOneBtn.visibility = View.VISIBLE
        refreshOneBtn.isEnabled = true
        sharedStorage!!.setRefreshMode(1)
    }

    private fun activateVisualizer () {
        //get the AudioSessionId from your MediaPlayer and pass it to the visualizer
        try {
            requestAudioPermissions(false)
            val audioSessionId = player!!.getAudioSessionId()
            if (audioSessionId != -1)
                mVisualizer.setAudioSessionId(audioSessionId)

//                recyclerAdapter?.setAudioSessionID(audioSessionId)

        } catch (e: Exception) {
            Log.e(TAG, "setUpMediaView: mVisualizer.setAudioSessionId - ${e.toString()}")
        }
    }

    private fun activateSeekBar() {
        seek_bar.max = player!!.getSeconds()

        runnable = Runnable {
            seek_bar.progress = player!!.getCurrentSeconds()
            tv_pass.text = player!!.getCurrentDurationInMinutes()

            handler.postDelayed(runnable, 1000)
        }

        handler.postDelayed(runnable, 1000)
    }

    // Request for AUDIO_RECORD Permission
    fun requestAudioPermissions(play: Boolean) {
        //If permission is granted, then go to play audio
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED && play) {

            Play()
        }

        else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Өлең тыңдау үшін, RECORD_AUDIO - ны қолдануға рұқсатыңызды беріңіз!", Toast.LENGTH_LONG).show()

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    MY_PERMISSIONS_RECORD_AUDIO)

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    MY_PERMISSIONS_RECORD_AUDIO)
            }
        }
    }

    private fun loadSongs() {
        linearLayoutManager = LinearLayoutManager(this)
        listOfSongs.layoutManager = linearLayoutManager
        songs = ArrayList()

        songs.add(Song(1, R.raw.song, "null", "Ауылым ай", "Шырқашы жүрек", "Gani Matebayev", true, "", ""))
        songs.add(Song(2, R.raw.song2, "null", "Дүнген қызы арманым", "Ауылды аңсау", "Gani Matebayev", false, "", ""))
        songs.add(Song(3, R.raw.song3, "null", "Достар", "Шырқашы жүрек", "Gani Matebayev",false, "", ""))
        songs.add(Song(4, R.raw.song3, "null", "Бауырларыма", "Ауылды аңсау", "Gani Matebayev", false, "", ""))
        songs.add(Song(5 ,R.raw.song3, "null", "Бұл сағыныш", "Шырқашы жүрек", "Gani Matebayev", true, "", ""))
        songs.add(Song(6 ,R.raw.song3, "null", "Аңғал досым", "Ауылды аңсау", "Gani Matebayev", true, "", ""))
        songs.add(Song(7 ,R.raw.song3, "null", "Әкені аңсау", "Шырқашы жүрек", "Gani Matebayev", false, "", ""))
        songs.add(Song(8 ,R.raw.song3, "null", "Ақ маржан", "Ауылды аңсау", "Gani Matebayev", false, "", ""))
        songs.add(Song(9 ,R.raw.song3, "null", "Сағындым ғой", "Ауылды аңсау", "Gani Matebayev", false, "", ""))

        songs.add(Song(10, R.raw.song, "null", "Ауылым ай", "Шырқашы жүрек", "Gani Matebayev", true, "", ""))
        songs.add(Song(11, R.raw.song2, "null", "Дүнген қызы арманым", "Ауылды аңсау", "Gani Matebayev", false, "", ""))
        songs.add(Song(12, R.raw.song3, "null", "Достар", "Шырқашы жүрек", "Gani Matebayev",false, "", ""))
        songs.add(Song(13, R.raw.song3, "null", "Бауырларыма", "Ауылды аңсау", "Gani Matebayev", false, "", ""))
        songs.add(Song(14 ,R.raw.song3, "null", "Бұл сағыныш", "Шырқашы жүрек", "Gani Matebayev", true, "", ""))
        songs.add(Song(15 ,R.raw.song3, "null", "Аңғал досым", "Ауылды аңсау", "Gani Matebayev", true, "", ""))
        songs.add(Song(16 ,R.raw.song3, "null", "Әкені аңсау", "Шырқашы жүрек", "Gani Matebayev", false, "", ""))
        songs.add(Song(17 ,R.raw.song3, "null", "Ақ маржан", "Ауылды аңсау", "Gani Matebayev", false, "", ""))
        songs.add(Song(18 ,R.raw.song3, "null", "Сағындым ғой", "Ауылды аңсау", "Gani Matebayev", false, "", ""))

        sharedStorage?.storeSongs(songs)

        recyclerAdapter = RecyclerAdapter(songs, this)

        listOfSongs.adapter = recyclerAdapter
    }

    private fun initMediaService() {
        //Check is service is active
        if (!serviceBound) {
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            // Service is active
            // Send media with BroadcastReceiver
            val broadcastIntent = Intent(sharedStorage!!.playNewSongReceiverAction)
            sendBroadcast(broadcastIntent)
            recyclerAdapter?.play()
        }
    }

    private fun Play() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            if (sharedStorage?.getPause()!!) {
                player?.resumeMedia()

                activateVisualizer()
                activateSeekBar()
                showPauseButton()

                recyclerAdapter?.play()
                sharedStorage?.setPause(false)
            }
        } else {
            requestAudioPermissions(true)
        }
    }

    private fun Pause() {
        if (!sharedStorage?.getPause()!!) {
            player?.pauseMedia()
        }

        showPlayButton()
        recyclerAdapter?.pause()
        sharedStorage?.setPause(true)
    }

    private fun Next() {
        player?.skipToNext()

        activateVisualizer()
        activateSeekBar()
        showPauseButton()

        recyclerAdapter?.next()
        sharedStorage?.setPause(false)

    }

    private fun Previous() {
        player?.skipToPrevious()

        activateVisualizer()
        activateSeekBar()
        showPauseButton()

        recyclerAdapter?.previous()
        sharedStorage?.setPause(false)
    }

    //Binding this Client to the AudioPlayer Service
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as MediaPlayerService.LocalBinder
            player = binder.service
            serviceBound = true

            Toast.makeText(this@MainActivity, "Service Bound", Toast.LENGTH_SHORT).show()

            when(sharedStorage!!.getPause()) {
                true -> {
                    showPlayButton()
                }
                false -> {
                    recyclerAdapter?.resume()

                    if (!serviceBound) {
                        initMediaService()
                    }
                    mediaPlayerContainer.visibility = View.VISIBLE
                }
            }

        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putBoolean("ServiceState", serviceBound)

        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)

        serviceBound = savedInstanceState!!.getBoolean("ServiceState")
    }


    // Handle request permissions
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            MY_PERMISSIONS_RECORD_AUDIO -> {

                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {

                    Log.i(TAG, "Permission has been denied by user")
                    Toast.makeText(this, "Рұқсат күтілуде!", Toast.LENGTH_LONG).show()
                } else {
                    Log.i(TAG, "Permission has been granted by user")
                    requestAudioPermissions(true)
                }
            }
        }
    }


    private fun createFolder(fileName: String){
        val folder = filesDir
        val f = File(folder, fileName)
        if (!f.exists()) {
            f.mkdirs()
            Log.d(TAG, "is folder created: ${f.absolutePath}")
        } else {
            Log.d(TAG, "folder is exists: ${f.absolutePath}")
        }

        val songs = File("$folder/$fileName", "songs")
        if (!songs.exists()) {
            songs.mkdir()
            Log.d(TAG, "songs: folder created: ${songs.absolutePath}")
        }

        val images = File("$folder/$fileName", "images")
        if (!images.exists()) {
            images.mkdir()
            Log.d(TAG, "images: folder created: ${images.absolutePath}")
        }

        // Create Folder in internal storage
//        var filepath = Environment.getExternalStorageDirectory().getPath()
//        val f = File(filepath, fileName)
//        val songs = File("$filepath/$fileName", "songs")
//        val imgs = File("$filepath/$fileName", "imgs")
//
//        var bsongs = songs.mkdir()
//        var bimgs = imgs.mkdir()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        toggle.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {

//        when (item.itemId) {
//            R.id.nav_item_one -> Toast.makeText(this, "Clicked item one", Toast.LENGTH_SHORT).show()
//            R.id.nav_item_two -> Toast.makeText(this, "Clicked item two", Toast.LENGTH_SHORT).show()
//            R.id.nav_item_four -> Toast.makeText(this, "Clicked item four", Toast.LENGTH_SHORT).show()
//        }
        return true
    }

    //setting menu in action bar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onStop() {
        super.onStop()

        if (sharedStorage?.getPause()!!) {
            player?.buildNotification(PlaybackStatus.PAUSED)
        } else {
            player?.buildNotification(PlaybackStatus.PLAYING)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (mVisualizer != null)
            mVisualizer.release()

        if (serviceBound) {
            unbindService(serviceConnection)

            player?.stopSelf()
        }

        unregisterReceiver(updateUIReciver)

        try {
            handler.removeCallbacks(runnable)
        } catch (e: Exception) {
            Log.d(TAG, "handler removeCallbacks: ${e.toString()}")
        }

        sharedStorage?.setPause(true)
    }

}
