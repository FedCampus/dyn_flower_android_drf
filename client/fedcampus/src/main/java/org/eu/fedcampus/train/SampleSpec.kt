package org.eu.fedcampus.train

data class SampleSpec<X, Y>(
    val convertX: (List<X>) -> Array<X>,
    val convertY: (List<Y>) -> Array<Y>,
    /** Create an array of empty [Y]. */
    val emptyY: (Int) -> Array<Y>,
    /** Given test samples and logits, calculate test loss. */
    val loss: (MutableList<Sample<X, Y>>, Array<Y>) -> Float,
    /** Given test samples and logits, calculate test accuracy. */
    val accuracy: (MutableList<Sample<X, Y>>, Array<Y>) -> Float,
)
