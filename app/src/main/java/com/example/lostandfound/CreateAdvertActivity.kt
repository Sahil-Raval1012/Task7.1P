package com.example.lostandfound
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.lostandfound.databinding.ActivityCreateBinding
import com.example.lostandfound.db.DatabaseHelper
import com.example.lostandfound.db.Item
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
class CreateAdvertActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateBinding
    private var imagePath: String? = null
    private var pendingCameraFile: File? = null
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveImageLocally(it) }
    }
    private val pickFromFiles = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { saveImageLocally(it) }
    }
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val file = pendingCameraFile
        if (success && file != null && file.exists() && file.length() > 0) {
            imagePath = file.absolutePath
            binding.imagePreview.setImageURI(Uri.fromFile(file))
            binding.imagePreview.visibility = android.view.View.VISIBLE
        } else {
            file?.delete()
            Toast.makeText(this, getString(R.string.image_error), Toast.LENGTH_SHORT).show()
        }
        pendingCameraFile = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val categories = resources.getStringArray(R.array.categories_create)
        binding.spinnerCategory.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, categories
        )
        binding.btnPickImage.setOnClickListener { showImageSourceChooser() }
        binding.btnPickDate.setOnClickListener { showDatePicker() }
        binding.btnSave.setOnClickListener { saveItem() }
    }
    private fun showImageSourceChooser() {
        val options = arrayOf(
            getString(R.string.image_from_camera),
            getString(R.string.image_from_gallery),
            getString(R.string.image_from_files),
            getString(R.string.image_from_sample)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.image_source_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera()
                    1 -> pickImage.launch("image/*")
                    2 -> pickFromFiles.launch(arrayOf("image/*"))
                    3 -> showSampleChooser()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    private fun showSampleChooser() {
        val labels = arrayOf(
            getString(R.string.sample_wallet),
            getString(R.string.sample_keys),
            getString(R.string.sample_phone)
        )
        val resIds = intArrayOf(
            R.drawable.sample_keys,
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.image_from_sample)
            .setItems(labels) { _, which ->
                saveSampleDrawable(resIds[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    private fun saveSampleDrawable(resId: Int) {
        try {
            val drawable: Drawable = ContextCompat.getDrawable(this, resId)
                ?: throw IllegalStateException("Sample image not found")
            val bmp: Bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val size = 600
                val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawColor(Color.WHITE)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                out
            }
            val dir = File(filesDir, "images").apply { if (!exists()) mkdirs() }
            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            imagePath = file.absolutePath
            binding.imagePreview.setImageURI(Uri.fromFile(file))
            binding.imagePreview.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.image_error), Toast.LENGTH_SHORT).show()
        }
    }
    private fun launchCamera() {
        try {
            val dir = File(filesDir, "images").apply { if (!exists()) mkdirs() }
            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
            pendingCameraFile = file
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            takePicture.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.image_error), Toast.LENGTH_SHORT).show()
        }
    }
    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        android.app.DatePickerDialog(
            this,
            { _, y, m, d ->
                val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                binding.editDate.setText(formatted)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    private fun saveImageLocally(uri: Uri) {
        try {
            val dir = File(filesDir, "images").apply { if (!exists()) mkdirs() }
            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            imagePath = file.absolutePath
            binding.imagePreview.setImageURI(Uri.fromFile(file))
            binding.imagePreview.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.image_error), Toast.LENGTH_SHORT).show()
        }
    }
    private fun saveItem() {
        val postType = if (binding.radioLost.isChecked) "Lost" else "Found"
        val name = binding.editName.text.toString().trim()
        val phone = binding.editPhone.text.toString().trim()
        val description = binding.editDescription.text.toString().trim()
        val date = binding.editDate.text.toString().trim()
        val location = binding.editLocation.text.toString().trim()
        val category = binding.spinnerCategory.selectedItem?.toString() ?: "Other"
        if (name.isEmpty() || phone.isEmpty() || description.isEmpty() ||
            date.isEmpty() || location.isEmpty()
        ) {
            Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }
        if (imagePath.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.image_required), Toast.LENGTH_SHORT).show()
            return
        }
        val createdAt = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val item = Item(
            postType = postType,
            name = name,
            phone = phone,
            description = description,
            date = date,
            location = location,
            category = category,
            imagePath = imagePath,
            createdAt = createdAt
        )
        val id = DatabaseHelper(this).insertItem(item)
        if (id > 0) {
            Toast.makeText(
                this,
                getString(R.string.saved_at, sdf.format(Date(createdAt))),
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(this, ListActivity::class.java))
            finish()
        } else {
            Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
