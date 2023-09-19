var ICON_FILENAME = 'icon.png';
var RES_PATH = 'platforms/android/res/';

var map_folders = [
  {
		src: "mipmap-hdpi",
		dst: "drawable-hdpi"
	},
	{
		src: "mipmap-ldpi",
		dst: "drawable-ldpi"
	},
	{
		src: "mipmap-mdpi",
		dst: "drawable-mdpi"
	},
	{
		src: "mipmap-xhdpi",
		dst: "drawable-xhdpi"
	},
	{
		src: "mipmap-xxhdpi",
		dst: "drawable-xxhdpi"
	},
	{
		src: "mipmap-xxxhdpi",
		dst: "drawable-xxxhdpi"
	}];

module.exports = function (ctx) {

  // make sure android platform is part of build
  if (ctx.opts.platforms.indexOf('android') < 0) {
    return;
  }

  console.error("Running hook to create drawables");

  var fs = ctx.requireCordovaModule('fs');
  var path = ctx.requireCordovaModule('path');
  var ConfigParser = ctx.requireCordovaModule('cordova-common').ConfigParser;

  var appConfig = new ConfigParser(path.join(ctx.opts.projectRoot, "config.xml"));

  var engines = appConfig.getEngines();

  if(engines.length <= 0){
    return;
  }

  // Matches first digit (eg. ^8.0.0 returns 8) 
  var engineMajor = engines[0].spec.match(/(\d+)/)[0];

  // If hook is running on project with cordova-android higher or equal than 8, redefine global variables
  if(Number(engineMajor) >= 8){
    ICON_FILENAME = 'ic_launcher.png';
    RES_PATH = 'platforms/android/app/src/main/res/';
  }

  map_folders.forEach(function(folder) {
    var drawable_folder = path.join(ctx.opts.projectRoot, RES_PATH, folder.dst);

    if (!fs.existsSync(drawable_folder)){
      fs.mkdirSync(drawable_folder);
    }
    else{
      console.log("Folder " + folder.dst + " already exists");
    }

    var res_dir = path.join(ctx.opts.projectRoot, RES_PATH);

    var src = path.join(res_dir, folder.src, ICON_FILENAME);
    var dst = path.join(res_dir, folder.dst, ICON_FILENAME);

    copyFile(fs, src, dst);
  });

  console.log("All icons copied with success");
};


function copyFile(fs, src, dst) {
  var rs = fs.createReadStream(src);

  rs.on('error', function(err){
    if (err) throw err;
  });

  rs.pipe(fs.createWriteStream(dst));
}