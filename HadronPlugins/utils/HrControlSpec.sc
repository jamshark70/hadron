
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

	constrain { arg value;
		^value.max(clipLo).min(clipHi).round(step)
	}
	map { arg value;
		// maps a value from [0..1] to spec range
		^warp.map(value.max(0.0).min(1.0)).round(step);
	}
	unmap { arg value;
		// maps a value from spec range to [0..1]
		^warp.unmap(value.round(step).max(clipLo).min(clipHi));
	}
}