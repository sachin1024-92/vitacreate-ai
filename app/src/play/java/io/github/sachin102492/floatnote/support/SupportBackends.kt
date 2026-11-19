package io.github.sachin102492.floatnote.support

import android.content.Context

object SupportBackends {
    fun create(context: Context): SupportBackend = PlaySupportBackend(context)
}
