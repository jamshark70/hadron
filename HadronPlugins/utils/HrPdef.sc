/*

With apologies to Julian Rohrhuber and the rest of the JITLib crew for lifting these classes wholesale.

I need dependent notifications for every change that these objects absorb, and I don't want to use method overrides to do it.

-hjh

*/

HrPatternProxy : PatternProxy {
	source_ { |item|
		super.source = item;
		this.changed(\source, item);
	}

	printOn { |stream|
		^this.storeOn(stream);
	}

	*exists { |key|
		if(this.respondsTo(\all)) {
			^this.all[key].notNil
		} {
			Error("% does not have a collection of patterns".format(this.name)).throw;
		};
	}
}

HrEventPatternProxy : EventPatternProxy {
	source_ { |item|
		super.source = item;
		this.changed(\source, item);
	}

	printOn { |stream|
		^this.storeOn(stream);
	}

	*exists { |key|
		if(this.respondsTo(\all)) {
			^this.all[key].notNil
		} {
			Error("% does not have a collection of patterns".format(this.name)).throw;
		};
	}

	quant_ { arg val;
		try { source.quant = val };
		quant = val;
		this.changed(\quant, val);
	}
}

HrPdef : HrEventPatternProxy {
	var <key;

	classvar <>all;

	storeArgs { ^[key] }

	*keys { ^all.keys }
	*hasGlobalDictionary { ^true }  // for 3.5+

	*new { arg key, item;
		var res = this.at(key);
		if(res.isNil) {
			res = super.new(item).prAdd(key);
		} {
			if(item.notNil) { res.source = item }
		}
		^res
	}

	map { arg ... args;
		if(envir.isNil) { this.envir = () };
		args.pairsDo { |key, name| envir.put(key, HrPdefn(name)) }
	}

	prAdd { arg argKey;
		key = argKey;
		all.put(argKey, this);
		// if not "this.class" then I would have to add dependants to every HrPdef
		this.class.changed(\added);
	}

	remove {
		this.class.all.removeAt(this.key);
		this.clear;
		this.class.changed(\removed);
	}

	*initClass {
		all = IdentityDictionary.new;
	}
}



HrPbindProxy : Pattern {
	var <>pairs, <source;

	*new { arg ... pairs;
		^super.newCopyArgs(pairs).init
	}
	init {
		forBy(0, pairs.size-1, 2) { arg i;
			pairs[i+1] = HrPatternProxy(pairs[i+1])
		};
		source = HrEventPatternProxy(Pbind(*pairs));
	}
	embedInStream { arg inval;
		^source.embedInStream(inval)
	}
	find { arg key;
		// this is u === key in the original
		// but I need to match HrMonoTarget objects too
		pairs.pairsDo { |u,x,i| if(u == key) { ^i } }; ^nil
	}
	quant_ { arg val;
		pairs.pairsDo { arg key, item; item.quant = val }; // maybe use ref later
		source.quant = val;
	}
	quant { ^source.quant }
	envir { ^source.envir }
	envir_ { arg envir; source.envir_(envir) }

	at { arg key; var i; i = this.find(key); ^if(i.notNil) { pairs[i+1] } { nil } }

	// does not yet work with adding arrayed keys/values
	set { arg ... args; // key, val ...
		var changedPairs=false, quant, savePairs = pairs.copy;
		quant = this.quant;
		args.pairsDo { |key, val|
			var i, remove;
			i = this.find(key);
			if(i.notNil)
			{
				if(val.isNil) {
					pairs.removeAt(i);
					pairs.removeAt(i);
					changedPairs = true;
				}{
					pairs[i+1].source = val;
				};
			}{
				pairs = pairs ++ [key, HrPatternProxy.new.setSourceLikeInPbind(val).quant_(quant)];
				// fin(inf) is a way to stream symbols endlessly
				changedPairs = true;
			};

		};
		if(changedPairs) {
			source.source = Pbind(*pairs);
			this.changed(\pairs, savePairs);
		};
	}

	storeArgs { ^pairs.collect(_.source) }
}


HrPbindef : HrPdef {
	*new { arg ... pairs;
		var key, pat, src, changedSrc = false;
		key = pairs.removeAt(0);
		if(this.exists(key).not and: { pairs.isEmpty }) { ^nil };
		pat = super.new(key);
		src = pat.source;
		if(src.isKindOf(HrPbindProxy).not) {
			case
			{ src.isKindOf(Pbind) } {
				src = HrPbindProxy(*(src.patternpairs));
				changedSrc = true;
			}
			{ src.isNil and: { pairs.notEmpty } } {
				src = HrPbindProxy(*pairs);
				pairs = [];  // don't trigger 'set' below
				changedSrc = true;
			}
			{ src.notNil } {
				// pre-existing pattern can't be converted to pbindproxy
				// so return existing
				// in theory I could make it a pchain containing a pbindproxy
				// but then setting gets really messy
				^pat
			};
		};
		// src MUST be a HrPbindProxy by now (either it is, or we never reach here)
		if(pairs.notEmpty) {
			src.set(*pairs);
			if(changedSrc.not) {
				pat.wakeUp;
				pat.changed(\pairs, pairs)
			};
		};
		if(changedSrc) {
			pat.source = src;  // will send notification in here
		};

		^pat
	}

	storeArgs { ^[key]++pattern.storeArgs }
	repositoryArgs { ^this.storeArgs }
}


HrPdefn : HrPatternProxy {
	var <key;
	classvar <>all;

	*initClass {
		all = IdentityDictionary.new;
	}

	*keys { ^this.all.keys }

	*new { arg key, item;
		var res = this.at(key);
		if(res.isNil) {
			res = super.new(item).prAdd(key);
		} {
			if(item.notNil) { res.source = item }
		}
		^res

	}

	map { arg ... args;
		if(envir.isNil) { this.envir = () };
		args.pairsDo { |key, name| envir.put(key, HrPdefn(name)) }
	}

	storeArgs { ^[key] } // assume it was created globally

	prAdd { arg argKey;
		key = argKey;
		this.class.all.put(argKey, this);
		this.class.changed(\added, key);
	}

	remove {
		var saveKey = this.key;
		this.class.all.removeAt(saveKey);
		this.clear;
		this.class.changed(\removed, saveKey);
	}
}


// A pattern-y thing that serves as a modulation target

HrPMod : HrPdefn {
	classvar <>allMods;
	var <spec;

	*initClass {
		allMods = IdentityDictionary.new;
		StartUp.add {
			Library.put(HrPMod, \modSets, IdentityDictionary.new);
			Library.put(HrPMod, \modGets, IdentityDictionary.new);
		};
	}

	*all { ^allMods }
	*at { |key| ^allMods[key] }

	*new { |key, value, spec|
		var res, asSpec;
		if(#[startOrStop, start].includes(key)) {
			Error("HrPMod: %% is a reserved key for Hr[Poly|Mono]Pattern".format($\\, \abc)).throw;
		};
		res = this.at(key);
		if(res.isNil) {
			asSpec = spec.asSpec;
			res = super.new(key).source_(value ?? { asSpec.default }).spec_(asSpec);
		} {
			// not changing anything, so skip the updates - speed
			if(value.isNil and: { spec.isNil }) { ^res };
			asSpec = (spec ?? { res.spec }).asSpec;
			if(spec.notNil) { res.spec = asSpec };
			res.source = asSpec.constrain(value ?? { res.source ?? { asSpec.default } });
		};
		^res
	}

	value { ^source }
	value_ { |val| this.source = val }

	source_ { |val|
		// have to check val because the superclass does source_(nil) upon remove
		super.source = if(spec.notNil and: { val.notNil }) { spec.constrain(val) } { val };
	}

	spec_ { |newSpec|
		spec = newSpec.asSpec;
		if(source.notNil) { this.source = source };  // re-constrain
	}

	prAdd { |argKey|
		this.class.modGets.put(argKey, { source });
		this.class.modSets.put(argKey, { |argg| this.source = argg });
		super.prAdd(argKey);
	}

	remove {
		this.class.modGets.removeAt(key);
		this.class.modSets.removeAt(key);
		super.remove;
	}

	*modGets { ^Library.at(HrPMod, \modGets) }
	*modSets { ^Library.at(HrPMod, \modSets) }
	*modMapSets { ^Library.at(HrPMod, \modSets) }
}