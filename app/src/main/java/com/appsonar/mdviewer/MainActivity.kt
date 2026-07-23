package com.appsonar.mdviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.appsonar.common.update.UpdateChecker
import com.appsonar.common.update.UpdateUi
import com.appsonar.mdviewer.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var markwon: Markwon

    private var currentUri: Uri? = null
    private var currentFileName: String? = null
    private var isDirty = false
    private var showPreview = true
    private var suppressDirtyTracking = false

    private val updateChecker = UpdateChecker(VERSION_URL, BuildConfig.VERSION_CODE)

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            takePersistablePermission(uri)
            loadFile(uri)
        }
    }

    // Legt die Datei über den System-Dialog an (SAF) — wie beim Öffnen
    // braucht die App dafür keine Speicher-Berechtigung.
    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            takePersistablePermission(uri)
            startNewFile(uri)
        }
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Manche Provider vergeben keine dauerhaften Rechte; Lesen/Schreiben
            // funktioniert für die laufende Sitzung trotzdem.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        markwon = Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(LinkifyPlugin.create())
            .build()

        binding.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!suppressDirtyTracking && currentUri != null && !isDirty) {
                    isDirty = true
                    updateTitle()
                    invalidateOptionsMenu()
                }
            }
        })

        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                showPreview = checkedId == binding.buttonPreview.id
                applyMode()
            }
        }

        if (savedInstanceState != null) {
            currentUri = savedInstanceState.getString(STATE_URI)?.let(Uri::parse)
            currentFileName = savedInstanceState.getString(STATE_FILE_NAME)
            isDirty = savedInstanceState.getBoolean(STATE_DIRTY)
            showPreview = savedInstanceState.getBoolean(STATE_SHOW_PREVIEW, true)
        }

        binding.modeToggle.check(
            if (showPreview) binding.buttonPreview.id else binding.buttonCode.id
        )
        applyMode()
        updateTitle()

        // Stille Prüfung beim Start — zeigt nur etwas, wenn wirklich eine neue Version da ist.
        UpdateUi.checkSilently(this, binding.root, updateChecker)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Der Editor-Text ist erst jetzt wiederhergestellt — Vorschau neu rendern.
        applyMode()
        invalidateOptionsMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Der Text des Editors wird von Android automatisch gesichert (freezesText).
        outState.putString(STATE_URI, currentUri?.toString())
        outState.putString(STATE_FILE_NAME, currentFileName)
        outState.putBoolean(STATE_DIRTY, isDirty)
        outState.putBoolean(STATE_SHOW_PREVIEW, showPreview)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_save).isEnabled = currentUri != null && isDirty
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_new -> {
            confirmDiscardIfDirty {
                createDocument.launch(getString(R.string.new_file_default_name))
            }
            true
        }
        R.id.action_open -> {
            confirmDiscardIfDirty { launchPicker() }
            true
        }
        R.id.action_save -> {
            saveFile()
            true
        }
        R.id.action_check_update -> {
            UpdateUi.checkManually(this, binding.root, updateChecker)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun launchPicker() {
        // .md-Dateien werden je nach Provider als text/markdown, text/plain oder
        // application/octet-stream gemeldet, daher */* als Filter.
        openDocument.launch(arrayOf("*/*"))
    }

    private fun confirmDiscardIfDirty(action: () -> Unit) {
        if (!isDirty) {
            action()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_changes_title)
            .setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.discard) { _, _ -> action() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadFile(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IOException("Stream ist null")
        } catch (e: Exception) {
            Snackbar.make(
                binding.root,
                getString(R.string.error_open, e.localizedMessage ?: ""),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        currentUri = uri
        currentFileName = queryDisplayName(uri)
        suppressDirtyTracking = true
        binding.editor.setText(text)
        suppressDirtyTracking = false
        isDirty = false

        binding.emptyState.visibility = android.view.View.GONE
        showPreview = true
        binding.modeToggle.check(binding.buttonPreview.id)
        applyMode()
        updateTitle()
        invalidateOptionsMenu()
    }

    /** Frisch angelegte (leere) Datei: direkt im Code-Modus lostippen. */
    private fun startNewFile(uri: Uri) {
        currentUri = uri
        currentFileName = queryDisplayName(uri)
        suppressDirtyTracking = true
        binding.editor.setText("")
        suppressDirtyTracking = false
        isDirty = false

        binding.emptyState.visibility = android.view.View.GONE
        showPreview = false
        binding.modeToggle.check(binding.buttonCode.id)
        applyMode()
        updateTitle()
        invalidateOptionsMenu()
        binding.editor.requestFocus()
    }

    private fun saveFile() {
        val uri = currentUri ?: return
        try {
            // "wt" = write + truncate, damit kürzerer Text keine Altreste hinterlässt.
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(binding.editor.text.toString())
            } ?: throw IOException("Stream ist null")
            isDirty = false
            updateTitle()
            invalidateOptionsMenu()
            Snackbar.make(binding.root, R.string.saved, Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(
                binding.root,
                getString(R.string.error_save, e.localizedMessage ?: ""),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun applyMode() {
        val hasFile = currentUri != null
        binding.modeToggle.visibility =
            if (hasFile) android.view.View.VISIBLE else android.view.View.GONE
        binding.emptyState.visibility =
            if (hasFile) android.view.View.GONE else android.view.View.VISIBLE

        if (!hasFile) {
            binding.previewScroll.visibility = android.view.View.GONE
            binding.editorScroll.visibility = android.view.View.GONE
            return
        }

        if (showPreview) {
            markwon.setMarkdown(binding.preview, binding.editor.text.toString())
            binding.previewScroll.visibility = android.view.View.VISIBLE
            binding.editorScroll.visibility = android.view.View.GONE
        } else {
            binding.previewScroll.visibility = android.view.View.GONE
            binding.editorScroll.visibility = android.view.View.VISIBLE
        }
    }

    private fun updateTitle() {
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.subtitle = currentFileName?.let {
            if (isDirty) "$it *" else it
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: uri.lastPathSegment

    companion object {
        private const val VERSION_URL = "https://github.com/appsonar/app-markdown-viewer/releases/download/latest/markdown-viewer-version.json"
        private const val STATE_URI = "state_uri"
        private const val STATE_FILE_NAME = "state_file_name"
        private const val STATE_DIRTY = "state_dirty"
        private const val STATE_SHOW_PREVIEW = "state_show_preview"
    }
}
