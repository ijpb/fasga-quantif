// Macro pour appliquer le meme traitement à toutes les images d'un repertoire
//
// Les repertoires sont choisis interactivement, 
// puis chaque image est traitee successivement et sauvegardee.

// On choisit les repertoires source et destination
dir1 = getDirectory("Choose Source Directory ");
dir2 = getDirectory("Choose Destination Directory "); 

// Extraction de la lste des images a traiter
list = getFileList(dir1);

// On itere sur les images
setBatchMode(false);
for (i=0; i<list.length; i++) {
   // affiche la progression dans la barre de menu d'ImageJ
   showProgress(i+1, list.length);
   
   // charge l'image courante
   path = dir1+list[i];
   open(path);

   // on renomme l'image pour eviter les noms trop longs
   currentName = list[i];
   dotIndex = lastIndexOf(currentName, ".");
   if (dotIndex!=-1)
      currentName = substring(currentName, 0, dotIndex); // remove extension

   // Applique les traitements a l'image
   // (a modifier selon les cas)
   run("Remove Black Border");
   run("Multiply...", "value=2");
   rename("current");

   
   run("Fasga Color Filtering", "cell=6 bright=12 gaussian=4");
   run("Fasga Region Classification", "dark=130 red=170");

   // calcule le nom de fichier du resultat
   path2 = dir2 + list[i];
   dotIndex = lastIndexOf(path2, ".");
   if (dotIndex!=-1)
      path2 = substring(path2, 0, dotIndex); // remove extension

   // sauve l'image resultat
   selectWindow("current-filtered-regionsRGB");
   save(path2 + "-regionsRGB.tif");
   close("current-filtered-regionsRGB");
   
   //save(path + ".tif");
   
   selectWindow("current-filtered");
   rename(currentName);
   run("Fasga Region Quantification", currentName+" label=current-filtered-regions resolution=1");

   // ferme les images intermediaires
   close("current-filtered-regions");
   close("Blue Region");
   close("Red Region");
   close("Bundles");
   close("Dark Regions");
   close("Brightness");
   close("Hue");
   close("current-filtered");
   close("current");

   // ferme l'image de depart
   close(list[i]);
}

// A la fin du processus, sauve le tableau de donnees
resultsTableName = "Quantif. Fasga";
getDateAndTime(year, month, dayOfWeek, dayOfMonth, hour, minute, second, msec);
dateString = "-" + year + "." + month + "." + dayOfMonth;
selectWindow(resultsTableName);
saveAs("Text", dir2 + resultsTableName + dateString + ".txt");

