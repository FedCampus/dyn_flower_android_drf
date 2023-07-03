package org.eu.fedcampus.train.helpers

import org.eu.fedcampus.train.Sample

fun <X> classifierAccuracy(
    samples: MutableList<Sample<X, FloatArray>>,
    logits: Array<FloatArray>
): Float {
    return averageLossWith(samples, logits) { sample, logit ->
        sample.label[logit.argmax()]
    }
}
