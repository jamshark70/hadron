GUI.qt;

g.boot;
GUI.swing;


(
w = Window(\test, Rect(700, 100, 600, 400));
z = HrPatternLine(w, Rect(2, 2, 596, 24), "test", nil, "Pwhite.new", 0);
w.front;
)

(
w = Window(\test, Rect(700, 100, 600, 400));
z = HrPatternLine(w, Rect(2, 2, 596, 24), "test", nil, nil, 0);
w.front;
)

z.isLast = true;
z.isLast = false;

z.index = 8;

z.model
z.model.source = Pexprand.new;

z.remove;

f = { |...args| args.debug("dependant") };

z.addDependant(f);
z.removeDependant(f);

x = z.model;

x.dependants;



HrPdef(\x, Pbind(\degree, Pseries(0, 1, inf)));

HrPdef(\x).source.postcs

HrPbindef(\x, \dur, 0.1);

HrPbindef(\x).source.pairs

HrPbindef(\x).isKindOf(HrEventPatternProxy)

HrPbindef.findRespondingMethodFor(\set).openCodeFile

HrPbindef.all



(
w = Window(\test, Rect(750, 100, 600, 120));
z = HrPbindefEditor(w, w.view.bounds.insetBy(2, 2));
w.front;

HrPdef(\x, Pbind(\degree, Pwhite(0, 7, inf), \dur, 0.1));
z.key = \x;
)

f = { |...args| args.debug("dependant") };

z.addDependant(f);
z.removeDependant(f);

Error.debug = true;
Error.debug = false;

z.focusedRow
z[0].hasFocus

z.view.children
z.resize = 5;

z.view.children[0].bounds

z.view.children[0].resize

z.view.children[0].children[1].bounds
.resize

z.view.children[0].children[1].children.last.resize

z.view.children[0].children[1].children.last.parents.do { |view| [view, view.resize].postln };

HrPbindef(\x, \dur, Pexprand(0.05, 0.25, inf));
HrPbindef(\x, \amp, Pexprand(0.1, 0.5, inf));
HrPbindef(\x, \amp, nil);

HrPdef(\x).source.postcs
focus
z.slotAt(\subpats)[1].model

z.status

z.model.source.source
z.model.inspect
z.model.dependants

z.model.source.pairs

Object.dependantsDictionary.keys

HrPdef(\x, Pbind(\degree, Pwhite(0, 7, inf), \dur, 0.1));
HrPdef(\x).source.inspect;

HrPdef(\x, Pseq([1, 2], 1));  // test bad pattern

HrPbindef(\x);
HrPdef(\x).source.inspect;

HrPdef(\y, Pseq([1, 2]));
HrPbindef(\y);
HrPbindef(\y).source.inspect;

HrPbindef(\x, \a, 1);

HrPdef(\x).source.postcs
HrPdef(\x).source.insp



h = Hadron.new;

HrPbindef(\test, \dur, 1).inspect

Object.dependantsDictionary.keys

p = h.alivePlugs[0];
p.key.dump
p.slotAt(\pdefMenu).items.postcs

p.insp

p.slotAt(\pdefMenu).items.detectIndex { |name| name.asSymbol == p.key } ? 0;

HrPbindef(\a, \dur, 2);
HrPbindef(\a)

HrPbindef.all.insp

HrPbindef.all[\a]

e = p.slotAt(\subpatEdit);
e.focusedRow
e[0].view.background

e[0].view.background = Color(0.8, 1, 0.8);

HrPbindef(\b).remove;
HrPbindef.keys

HrPbindef(\test)

HrPbindef.exists(\a)
HrPbindef(\a)

Meta_HrPbindef.findRespondingMethodFor(\exists)