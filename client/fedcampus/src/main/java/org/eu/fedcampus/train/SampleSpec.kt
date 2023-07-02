package org.eu.fedcampus.train

data class SampleSpec<X, Y>(
    val convertX: (List<X>) -> Array<X>,
    val convertY: (List<Y>) -> Array<Y>,
    /** Create an empty instance of [Y]. */
    val emptyY: () -> Y,
)
