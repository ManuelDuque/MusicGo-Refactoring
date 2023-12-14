package com.unex.musicgo.ui.activities

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.unex.musicgo.database.MusicGoDatabase
import com.unex.musicgo.databinding.LoginBinding
import com.unex.musicgo.ui.vms.LoginActivityViewModel
import com.unex.musicgo.ui.vms.factories.LoginActivityViewModelFactory

class LoginActivity : AppCompatActivity() {

    companion object {
        const val TAG = "LoginActivity"

        fun newIntent(packageContext: Context): Intent {
            return Intent(packageContext, LoginActivity::class.java).apply {
                Log.d(TAG, "Creating login activity intent")
            }
        }
    }

    private var _binding: LoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginActivityViewModel by lazy {
        val database = MusicGoDatabase.getInstance(this)
        val auth = FirebaseAuth.getInstance()
        val factory = LoginActivityViewModelFactory(database!!, auth)
        ViewModelProvider(this, factory)[LoginActivityViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = LoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpViewModel()

        binding.bind()
    }

    private fun setUpViewModel() {
        viewModel.toastLiveData.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        viewModel.isLoggedLiveData.observe(this) {
            val intent = HomeActivity.getIntent(this)
            startActivity(intent)
            finish()
        }
    }

    private fun LoginBinding.bind() {
        Log.d(TAG, "Binding login activity")
        this.loginBtn.setOnClickListener {
            val emailText = this.username.text.toString()
            val passwordText = this.passwordTv.text.toString()
            if (emailText.isEmpty() || passwordText.isEmpty()) {
                Toast.makeText(
                    baseContext,
                    "Please fill all the fields",
                    Toast.LENGTH_SHORT,
                ).show()
                return@setOnClickListener
            }
            login(emailText, passwordText)
        }
        this.registerBtn.setOnClickListener {
            val intent = SignupActivity.newIntent(this@LoginActivity)
            startActivity(intent)
        }
    }

    private fun login(email: String, password: String) {
        Log.d(TAG, "Logging in")
        Log.d(TAG, "Username: $email, Password: $password")
        viewModel.signInWithEmailAndPassword(email, password)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "Saving instance state")
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null // Avoid memory leaks
    }
}