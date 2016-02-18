// Macro de traitement en batch d'une serie d'images colorees au Fasga
//
// Les repertoires sont choisis interactivement, 
// puis chaque image est traitee successivement avec le meme jeu de parametres.
// Les traitements suivants sont effectues :
// * suppression des bords noirs de l'image
// * multiplication par deux pour corriger l'effet sombre
// * filtrage de l'image (ouverture morphologique, puis fermeture morphologique, puis lissage avec 
//		filtre gaussien)
// * detection de la region correspondant a la tige
// * detection des trous, basee sur la taille des zones claires
// * Detection des regions d'interet dans l'image
// * quantification de la morphometrie des differentes regions (Les resultats sont ajoutes au 
//		tableau de resultat courant)
// * ferme les differentes images ouvertes
// * sauvegarde l'ensemble des mesures dans un fichier texte avec le nom du repertoire et la date 
// 		courante
//
// Necessite:
// MorphoLibJ v1.1 ou supérieur
//
// Macro Fasga2_batch.ijm
// Version du 18/02/2016

// On choisit les repertoires source et destination
dir1 = getDirectory("Choose Source Directory ");
dir2 = getDirectory("Choose Destination Directory "); 

// Extraction de la liste des images a traiter
list = getFileList(dir1);

// On itere sur les images
setBatchMode(false);
for (i=0; i<list.length; i++) {
    // affiche la progression dans la barre de menu d'ImageJ
    showProgress(i+1, list.length);
   
    // Verifie que le fichier courant correspond a une image. Si non, passe au fichier suivant.
    if (!endsWith(list[i], ".tif")) {
		continue;
    }
		
    // charge l'image courante
    path = dir1+list[i];
    open(path);

    // on renomme l'image pour eviter les noms trop longs
    currentName = list[i];
    dotIndex = lastIndexOf(currentName, ".");
    if (dotIndex!=-1)
		currentName = substring(currentName, 0, dotIndex); // remove extension

	// Supprime les bords noirs, et corrige l'effet d'image sombre
	run("Remove Black Border");
	run("Multiply...", "value=2");
	rename("current");

	// Filtrage de l'image couleur, en utilisant des reglages par defaut pour les differents 
	// parametres
//   run("Color Filtering", "cell=6 bright=12 gaussian=4");
	run("Color Filtering", "cell=3 bright=12 gaussian=4");
  
	// Detection de la region correspondant a la tige
	selectWindow("current-filtered");
	run("Stem Segmentation", "high=0.9900 low=0.9700 bubbles=15");
	rename("stem");

    // Ajoute un filtrage base sur la taille des zones claires
    // -> on enleve les "trous" qui sont trop petits.
    // La taille est donnee en nombre de pixels.
    run("Invert");
    run("Size Opening 2D/3D", "min=200");
    run("Invert");
   
    // On remplace l'image appelee "stem" par le resultat du filtrage des trous
    close("stem");
    selectWindow("stem-sizeOpen");
    rename("stem");

    // Detection des regions d'interet dans l'image
    selectWindow("current-filtered");
    run("Regions Segmentation", "stem=stem dark=130 red=170 bundles=100");
   
    // calcule le nom de fichier du resultat
    path2 = dir2 + list[i];
    dotIndex = lastIndexOf(path2, ".");
    if (dotIndex != -1) {
       path2 = substring(path2, 0, dotIndex); // remove extension
    }

    // sauve l'image resultat (coloree en fonction de la region)
    selectWindow("current-filtered-regionsRGB");
    save(path2 + "-regionsRGB.tif");
    close("current-filtered-regionsRGB");

   
    // Reprend l'image label des regions, et quantifie la morphometrie
    // Les resultats sont ajoutes au tableau de resultat courant
    selectWindow("current-filtered");
    rename(currentName);
    run("Region Quantification", currentName+" label=current-filtered-regions resolution=1");

    // ferme les differentes images intermediaires
    close("stem");
    close("current-filtered-regions");
    close("Blue Region");
    close("Red Region");
    close("Bundles");
    close("Dark Regions");
    close("Brightness");
    close("Hue");
    //close("current-filtered");
    close(currentName);
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

