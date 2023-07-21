package org.eu.fedcampus.train.examples.fedmcrnn

import org.eu.fedcampus.train.SampleSpec
import org.eu.fedcampus.train.helpers.negativeLogLikelihoodLoss
import org.eu.fedcampus.train.helpers.placeholderAccuracy

fun sampleSpec() = SampleSpec<Float2DArray, FloatArray>(
    { it.toTypedArray() },
    { it.toTypedArray() },
    { Array(it) { FloatArray(1) } },
    ::negativeLogLikelihoodLoss,
    ::placeholderAccuracy,
)

const val DATA_TYPE = "FedMCRNN_7x8"

typealias Float2DArray = Array<FloatArray>
