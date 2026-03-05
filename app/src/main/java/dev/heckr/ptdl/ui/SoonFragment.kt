package dev.heckr.ptdl.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dev.heckr.ptdl.databinding.FragmentSoonBinding

class SoonFragment : Fragment() {

    private var _binding: FragmentSoonBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSoonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val label = findNavController().currentDestination?.label
        if (label != null) {
            binding.toolbar.title = label
        }

        binding.btnLearnMore.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hecker-01/ptdl")))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

