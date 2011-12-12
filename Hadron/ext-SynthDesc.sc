+ SynthDesc {
	symIsArrayArg { |argName|
		var cnameIndex = this.controls.detectIndex({ |cn| cn.name.asSymbol == argName }),
		cname;
		if(cnameIndex.isNil) {
			MethodError(
				"symIsArrayArg: No argument named % exists".format(argName.asCompileString),
				this
			).throw
		};
		cname = this.controls[cnameIndex];
		// 3.5-compatible quick check
		if(cname.defaultValue.size > 1) { ^true };
		// 3.4-compatible slower check
		// if it's the last index, this can't possibly be an array arg
		if(cnameIndex + 1 == this.controls.size) { ^false };
		if(this.controls[cnameIndex + 1].name.asSymbol == '?') { ^true }
			{ ^false };
	}
}