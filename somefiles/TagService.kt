package com.musicbible.service

import com.boostfield.spring.service.BaseService
import com.musicbible.model.Tag
import com.musicbible.repository.base.TagRepository

interface TagService<T : Tag> : TagRepository<T>, BaseService<T> {
    fun create(nameCN: String, nameEN: String? = null): T
}
