package com.clearwave.support

import dev.kensa.UseSetupStrategy
import dev.kensa.kotest.WithKotest
import dev.kensa.state.SetupStrategy
import dev.kensa.testng.KensaTest
import org.testng.annotations.Listeners

@Listeners(ClearwaveTestNgListener::class)
@UseSetupStrategy(SetupStrategy.Grouped)
abstract class ClearwaveTest : KensaTest, WithKotest
