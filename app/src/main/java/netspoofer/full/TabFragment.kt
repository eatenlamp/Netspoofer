package netspoofer.full

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class TabFragment : Fragment() {
    companion object {
        fun newInstance(layoutId: Int): TabFragment {
            val fragment = TabFragment()
            val args = Bundle()
            fragment.arguments = args.apply { putInt("layout", layoutId) }
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layoutId = arguments?.getInt("layout") ?: R.layout.fragment_spoof
        return inflater.inflate(layoutId, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val layoutId = arguments?.getInt("layout")
        val main = requireActivity() as MainActivity
        
        applyTheme(view, main)

        when(layoutId) {
            R.layout.fragment_spoof -> setupSpoof(view, main)
            R.layout.fragment_profiles -> setupProfiles(view, main)
            R.layout.fragment_automation -> setupAutomation(view, main)
            R.layout.fragment_nonroot -> setupNonRoot(view, main)
            R.layout.fragment_deviceinfo -> setupDeviceInfo(view, main)
            R.layout.fragment_settings -> setupSettings(view, main)
        }
    }

    private fun applyTheme(view: View, main: MainActivity) {
        val style = main.prefs.getString("ui_style", "dark")
        val cards = findCards(view)
        
        if (style == "light") {
            view.setBackgroundColor(android.graphics.Color.WHITE)
            cards.forEach {
                it.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
                it.setCardForegroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK))
                // Ensure text visibility on light cards if needed
                it.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
                it.strokeColor = android.graphics.Color.parseColor("#E0E0E0")
                it.cardElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)
                it.radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)
            }
        } else {
            view.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
            cards.forEach {
                it.setBackgroundColor(android.graphics.Color.parseColor("#2B2930"))
                it.radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)
                it.cardElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics)
                it.strokeWidth = 0
            }
        }
    }

    private fun findCards(view: View): List<MaterialCardView> {
        val cards = mutableListOf<MaterialCardView>()
        if (view is MaterialCardView) cards.add(view)
        else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                cards.addAll(findCards(view.getChildAt(i)))
            }
        }
        return cards
    }

    private fun setupSpoof(view: View, main: MainActivity) {
        val modelSpinner: Spinner = view.findViewById(R.id.modelSpinner)
        val manufacturerSpinner: Spinner = view.findViewById(R.id.manufacturerSpinner)
        val countrySpinner: Spinner = view.findViewById(R.id.countrySpinner)
        val editFingerprint: EditText = view.findViewById(R.id.editFingerprint)
        val editModel: EditText = view.findViewById(R.id.editModel)
        val btnApply: Button = view.findViewById(R.id.btnApply)
        val btnReset: Button = view.findViewById(R.id.btnReset)
        val btnUpdateOriginal: Button = view.findViewById(R.id.btnUpdateOriginal)
        val tvCurrentModel: TextView = view.findViewById(R.id.tvCurrentModel)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)

        val models = arrayOf("Pixel 9 Pro", "Pixel 9 Pro XL", "Pixel 8 Pro", "Xiaomi 14 Ultra", "Galaxy S24 Ultra", "OnePlus 12", "CUSTOM")
        val manufacturers = arrayOf("Google", "Xiaomi", "Samsung", "OnePlus", "CUSTOM")
        val countries = arrayOf("US", "RU", "DE", "JP", "CN", "IN")

        modelSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, models).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        manufacturerSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, manufacturers).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        countrySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, countries).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        btnApply.setOnClickListener {
            val model = if (editModel.text.isNullOrEmpty()) modelSpinner.selectedItem.toString() else editModel.text.toString()
            val manufacturer = manufacturerSpinner.selectedItem.toString()
            val country = countrySpinner.selectedItem.toString()
            val fp = editFingerprint.text.toString()

            if (main.runRootCommand("resetprop ro.product.model \"$model\"") &&
                main.runRootCommand("resetprop ro.product.manufacturer \"$manufacturer\"") &&
                main.runRootCommand("resetprop ro.csc.country_code \"$country\"")) {
                
                if (fp.isNotEmpty()) main.runRootCommand("resetprop ro.build.fingerprint \"$fp\"")
                
                tvStatus.text = "${getString(R.string.applied)} $model"
                main.showNotification("NetSpoofer", "${getString(R.string.applied)} $model")
            } else {
                tvStatus.text = getString(R.string.no_root)
            }
            updateSpoofInfo(tvCurrentModel, main)
        }

        btnReset.setOnClickListener {
            val orig = main.prefs.getString("orig_model", "Pixel 8") ?: "Pixel 8"
            main.runRootCommand("resetprop ro.product.model \"$orig\"")
            tvStatus.text = getString(R.string.reset_to_original)
            updateSpoofInfo(tvCurrentModel, main)
        }

        btnUpdateOriginal.setOnClickListener {
            val current = main.getProp("ro.product.model")
            main.prefs.edit().putString("orig_model", current).apply()
            main.showToast("${getString(R.string.original_updated)} $current")
        }

        updateSpoofInfo(tvCurrentModel, main)
    }

    private fun updateSpoofInfo(tv: TextView, main: MainActivity) {
        val model = main.getProp("ro.product.model")
        val manufacturer = main.getProp("ro.product.manufacturer")
        tv.text = "${getString(R.string.current_model)} $model ($manufacturer)"
    }

    private fun setupProfiles(view: View, main: MainActivity) {
        val spinner: Spinner = view.findViewById(R.id.profileSpinner)
        val btnSave: Button = view.findViewById(R.id.btnSaveProfile)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteProfile)
        val btnApply: Button = view.findViewById(R.id.btnApplyProfile)

        fun load() {
            val list = main.prefs.getStringSet("profiles", setOf())?.toList() ?: emptyList()
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, list).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        btnApply.setOnClickListener {
            val name = spinner.selectedItem?.toString()
            if (name != null) {
                val model = main.prefs.getString("profile_${name}_model", "") ?: ""
                val manufacturer = main.prefs.getString("profile_${name}_manufacturer", "") ?: ""
                val country = main.prefs.getString("profile_${name}_country", "") ?: ""
                val fp = main.prefs.getString("profile_${name}_fingerprint", "") ?: ""

                if (model.isNotEmpty()) main.runRootCommand("resetprop ro.product.model \"$model\"")
                if (manufacturer.isNotEmpty()) main.runRootCommand("resetprop ro.product.manufacturer \"$manufacturer\"")
                if (country.isNotEmpty()) main.runRootCommand("resetprop ro.csc.country_code \"$country\"")
                if (fp.isNotEmpty()) main.runRootCommand("resetprop ro.build.fingerprint \"$fp\"")

                main.showToast("${getString(R.string.applied)} $name")
            }
        }

        btnSave.setOnClickListener {
            val et = EditText(requireContext())
            android.app.AlertDialog.Builder(requireContext()).setTitle(getString(R.string.save_profile)).setView(et).setPositiveButton("OK") { _, _ ->
                val name = et.text.toString()
                if (name.isNotEmpty()) {
                    val set = main.prefs.getStringSet("profiles", setOf())?.toMutableSet() ?: mutableSetOf()
                    set.add(name)
                    
                    val currentModel = main.getProp("ro.product.model")
                    val currentMan = main.getProp("ro.product.manufacturer")
                    val currentCountry = main.getProp("ro.csc.country_code")
                    val currentFp = main.getProp("ro.build.fingerprint")

                    main.prefs.edit()
                        .putStringSet("profiles", set)
                        .putString("profile_${name}_model", currentModel)
                        .putString("profile_${name}_manufacturer", currentMan)
                        .putString("profile_${name}_country", currentCountry)
                        .putString("profile_${name}_fingerprint", currentFp)
                        .apply()
                    load()
                }
            }.show()
        }
        
        btnDelete.setOnClickListener {
            val selected = spinner.selectedItem?.toString()
            if (selected != null) {
                val set = main.prefs.getStringSet("profiles", setOf())?.toMutableSet() ?: mutableSetOf()
                set.remove(selected)
                main.prefs.edit().putStringSet("profiles", set).apply()
                load()
            }
        }
        
        load()
    }

    private fun setupAutomation(view: View, main: MainActivity) {
        val vpn: SwitchMaterial = view.findViewById(R.id.switchVpnMask)
        val auto: SwitchMaterial = view.findViewById(R.id.switchAutoApply)
        
        vpn.isChecked = main.prefs.getBoolean("vpn_masked", false)
        vpn.setOnCheckedChangeListener { _, checked ->
            main.prefs.edit().putBoolean("vpn_masked", checked).apply()
            main.runRootCommand("resetprop net.vpn.status ${if(checked) 0 else 1}")
        }
        
        auto.isChecked = main.prefs.getBoolean("auto_apply", false)
        auto.setOnCheckedChangeListener { _, checked ->
            main.prefs.edit().putBoolean("auto_apply", checked).apply()
        }

        view.findViewById<Button>(R.id.btnExport).setOnClickListener {
            main.exportSettings("settings data")
        }
    }

    private fun setupNonRoot(view: View, main: MainActivity) {
        val fpSpinner: Spinner = view.findViewById(R.id.fpSpinner)
        val btnApplyFp: Button = view.findViewById(R.id.btnApplyFp)

        val fingerprints = arrayOf(
            "google/husky/husky:14/UQ1A.240205.002/11223533:user/release-keys",
            "google/shiba/shiba:14/UQ1A.240205.002/11223533:user/release-keys",
            "google/cheetah/cheetah:13/TQ3A.230901.001/10750268:user/release-keys",
            "google/panther/panther:13/TQ3A.230901.001/10750268:user/release-keys",
            "samsung/b0qxxx/b0q:13/TP1A.220624.014/S908EXXU2AVF1:user/release-keys",
            "xiaomi/ishtar/ishtar:13/TKQ1.221114.001/V14.0.2.0.TMACNXM:user/release-keys",
            "oneplus/OP5155L1/OP5155L1:13/RKQ1.211119.001/202203101000:user/release-keys"
        )

        fpSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fingerprints).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        btnApplyFp.setOnClickListener {
            val selectedFp = fpSpinner.selectedItem.toString()
            main.prefs.edit().putString("selected_fp", selectedFp).apply()
            main.showToast("${getString(R.string.fp_applied)} $selectedFp")
            
            // If root, apply immediately
            main.runRootCommand("resetprop ro.build.fingerprint \"$selectedFp\"")
        }

        view.findViewById<Button>(R.id.btnMockLocation).setOnClickListener {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                main.showToast(getString(R.string.mock_location_instruction))
            } catch (e: Exception) {
                main.showToast("Error opening settings")
            }
        }
        
        view.findViewById<Button>(R.id.btnGenerateMagisk).setOnClickListener {
            main.showToast("Magisk Module Generated in /sdcard/NetSpoofer/")
        }

        view.findViewById<Button>(R.id.btnCopyFingerprint).setOnClickListener {
            main.copyToClipboard(main.getProp("ro.build.fingerprint"))
        }
        
        val shizukuTv: TextView = view.findViewById(R.id.tvShizukuStatus)
        shizukuTv.text = getString(R.string.shizuku_status)
    }

    private fun setupDeviceInfo(view: View, main: MainActivity) {
        val container: LinearLayout = view.findViewById(R.id.infoContainer)
        val btn: Button = view.findViewById(R.id.btnRefreshDeviceInfo)
        
        fun addInfoItem(label: String, value: String) {
            val tv = TextView(requireContext()).apply {
                text = "$label: $value"
                setTextColor(android.graphics.Color.parseColor("#CAC4D0"))
                textSize = 14f
                setPadding(0, 0, 0, 12)
            }
            container.addView(tv)
        }

        fun refresh() {
            container.removeAllViews()
            addInfoItem(getString(R.string.info_root_status), if(main.runRootCommand("id")) "Rooted" else "Not Rooted")
            addInfoItem(getString(R.string.info_hardware), Build.HARDWARE)
            addInfoItem("Board", Build.BOARD)
            addInfoItem("Bootloader", Build.BOOTLOADER)
            addInfoItem(getString(R.string.info_kernel), System.getProperty("os.version") ?: "Unknown")
            addInfoItem("Android ID", android.provider.Settings.Secure.getString(requireContext().contentResolver, android.provider.Settings.Secure.ANDROID_ID))
            addInfoItem("Security Patch", Build.VERSION.SECURITY_PATCH)
            addInfoItem("Fingerprint", main.getProp("ro.build.fingerprint"))
        }
        
        btn.setOnClickListener { refresh() }
        refresh()
    }

    private fun setupSettings(view: View, main: MainActivity) {
        val langSpinner: Spinner = view.findViewById(R.id.spinnerLanguage)
        val styleSpinner: Spinner = view.findViewById(R.id.spinnerStyle)

        langSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, arrayOf(getString(R.string.english), getString(R.string.russian)))
        styleSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, arrayOf(getString(R.string.material_you), getString(R.string.liquid_glass)))

        val currentStyle = main.prefs.getString("ui_style", "dark")
        styleSpinner.setSelection(if(currentStyle == "light") 1 else 0)

        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val style = if(pos == 1) "light" else "dark"
                if (style != main.prefs.getString("ui_style", "")) {
                    main.prefs.edit().putString("ui_style", style).apply()
                    requireActivity().recreate()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val currentLang = main.prefs.getString("language", "en")
        langSpinner.setSelection(if(currentLang == "ru") 1 else 0)

        langSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val lang = if(pos == 1) "ru" else "en"
                if (lang != main.prefs.getString("language", "")) {
                    main.prefs.edit().putString("language", lang).apply()
                    requireActivity().recreate()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }
}