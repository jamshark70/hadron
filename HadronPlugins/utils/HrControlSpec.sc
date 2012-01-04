
HrControlSpec : ControlSpec {
	init {
		warp = warp.asWarp(this);
		clipLo = min(minval, maxval);
		clipHi = max(minval, maxval);
	}
}