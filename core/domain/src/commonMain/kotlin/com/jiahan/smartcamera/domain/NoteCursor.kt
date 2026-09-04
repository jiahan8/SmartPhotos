package com.jiahan.smartcamera.domain

/**
 * Opaque position marker for note pagination.
 *
 * A caller receives one in [NotePage.nextCursor] and passes it back to fetch the following page.
 * What it points at is the data layer's business, so no data-source type has to cross the
 * repository boundary and no caller can construct a position the data layer did not issue.
 */
interface NoteCursor