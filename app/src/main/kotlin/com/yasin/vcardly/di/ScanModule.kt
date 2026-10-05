package com.yasin.vcardly.di

import com.yasin.vcardly.core.ocr.MlKitOcrEngine
import com.yasin.vcardly.core.ocr.OcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {
    @Binds abstract fun ocrEngine(impl: MlKitOcrEngine): OcrEngine
}
