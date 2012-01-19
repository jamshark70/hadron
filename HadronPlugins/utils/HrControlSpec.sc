
HrControlSpec : ControlSpec {
	*newFrom { |aControlSpec|
		//		^this.new(aControlSpec.minval, aControlSpec.maxval, aControlSpec.warp, aControlSpec.step, aControlSpec.default, aControlSpec.units)
		^this.new(*aControlSpec.asSpec.storeArgs)
	}

	init {
		warp = warp.asWarp(this);
		clipLo = min(minval, maxval);
		clipHi = max(minval, maxval);
	}
}