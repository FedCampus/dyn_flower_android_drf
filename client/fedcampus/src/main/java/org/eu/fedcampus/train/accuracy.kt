package org.eu.fedcampus.train

fun <X> classifierAccuracy(
    samples: MutableList<Sample<X, FloatArray>>,
    logits: Array<FloatArray>
): Float {
    return averageLossWith(samples, logits) { sample, logit ->
        sample.label[logit.indices.maxBy { logit[it] }]
    }
}
