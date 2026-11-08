package com.wishwell.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIconOption(val key: String, val label: String, val icon: ImageVector)

object CategoryIcons {
    val all: List<CategoryIconOption> = listOf(
        CategoryIconOption("category", "General", Icons.Rounded.Category),
        CategoryIconOption("star", "Favorites", Icons.Rounded.Star),
        CategoryIconOption("heart", "Wishlist", Icons.Rounded.Favorite),
        CategoryIconOption("gift", "Gifts", Icons.Rounded.CardGiftcard),
        CategoryIconOption("shopping", "Shopping", Icons.Rounded.ShoppingBag),
        CategoryIconOption("light", "Ideas", Icons.Rounded.Lightbulb),
        CategoryIconOption("sparkle", "Special", Icons.Rounded.AutoAwesome),
        CategoryIconOption("tech", "Tech", Icons.Rounded.Devices),
        CategoryIconOption("laptop", "Laptop", Icons.Rounded.LaptopMac),
        CategoryIconOption("phone", "Phone", Icons.Rounded.PhoneIphone),
        CategoryIconOption("computer", "Computer", Icons.Rounded.Computer),
        CategoryIconOption("audio", "Audio", Icons.Rounded.Headphones),
        CategoryIconOption("camera", "Camera", Icons.Rounded.CameraAlt),
        CategoryIconOption("photo", "Photography", Icons.Rounded.PhotoCamera),
        CategoryIconOption("gaming", "Gaming", Icons.Rounded.SportsEsports),
        CategoryIconOption("watch", "Watch", Icons.Rounded.Watch),
        CategoryIconOption("home", "Home", Icons.Rounded.Home),
        CategoryIconOption("furniture", "Furniture", Icons.Rounded.Weekend),
        CategoryIconOption("chair", "Seating", Icons.Rounded.Chair),
        CategoryIconOption("kitchen", "Kitchen", Icons.Rounded.Kitchen),
        CategoryIconOption("garden", "Garden", Icons.Rounded.Yard),
        CategoryIconOption("plants", "Plants", Icons.Rounded.LocalFlorist),
        CategoryIconOption("tools", "Tools", Icons.Rounded.Build),
        CategoryIconOption("fashion", "Fashion", Icons.Rounded.Checkroom),
        CategoryIconOption("jewelry", "Jewelry", Icons.Rounded.Diamond),
        CategoryIconOption("beauty", "Beauty", Icons.Rounded.FaceRetouchingNatural),
        CategoryIconOption("spa", "Self care", Icons.Rounded.Spa),
        CategoryIconOption("fitness", "Fitness", Icons.Rounded.FitnessCenter),
        CategoryIconOption("sports", "Sports", Icons.Rounded.SportsSoccer),
        CategoryIconOption("bike", "Cycling", Icons.Rounded.PedalBike),
        CategoryIconOption("outdoors", "Outdoors", Icons.Rounded.Backpack),
        CategoryIconOption("travel", "Travel", Icons.Rounded.Flight),
        CategoryIconOption("car", "Auto", Icons.Rounded.DirectionsCar),
        CategoryIconOption("books", "Books", Icons.Rounded.Book),
        CategoryIconOption("music", "Music", Icons.Rounded.MusicNote),
        CategoryIconOption("art", "Art", Icons.Rounded.Palette),
        CategoryIconOption("craft", "Crafts", Icons.Rounded.Brush),
        CategoryIconOption("work", "Work", Icons.Rounded.Work),
        CategoryIconOption("food", "Food", Icons.Rounded.LocalDining),
        CategoryIconOption("restaurant", "Dining", Icons.Rounded.Restaurant),
        CategoryIconOption("coffee", "Coffee", Icons.Rounded.LocalCafe),
        CategoryIconOption("pets", "Pets", Icons.Rounded.Pets),
        CategoryIconOption("baby", "Baby", Icons.Rounded.ChildCare),
        CategoryIconOption("toys", "Toys", Icons.Rounded.Toys),
        CategoryIconOption("celebration", "Celebration", Icons.Rounded.Cake)
    )

    private val byKey = all.associateBy { it.key }

    fun resolve(iconKey: String?, name: String?): ImageVector {
        iconKey?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { key ->
            byKey[key]?.icon?.let { return it }
        }
        return when (name?.trim()?.lowercase()) {
            "tech", "technology", "electronics", "gadgets" -> Icons.Rounded.Devices
            "audio", "headphones", "earphones", "iem", "iems" -> Icons.Rounded.Headphones
            "gaming", "games" -> Icons.Rounded.SportsEsports
            "books", "book", "reading" -> Icons.Rounded.Book
            "home", "house", "furniture" -> Icons.Rounded.Home
            "kitchen" -> Icons.Rounded.Kitchen
            "fashion", "clothing", "clothes", "style", "apparel" -> Icons.Rounded.Checkroom
            "fitness", "sports", "gym", "health", "workout" -> Icons.Rounded.FitnessCenter
            "travel", "trips" -> Icons.Rounded.Flight
            "food", "groceries" -> Icons.Rounded.LocalDining
            "beauty", "skincare", "makeup" -> Icons.Rounded.FaceRetouchingNatural
            "pets", "pet" -> Icons.Rounded.Pets
            "baby", "kids" -> Icons.Rounded.ChildCare
            "car", "auto", "automotive" -> Icons.Rounded.DirectionsCar
            "music" -> Icons.Rounded.MusicNote
            "camera", "photography" -> Icons.Rounded.CameraAlt
            "work", "office" -> Icons.Rounded.Work
            "gifts", "gift" -> Icons.Rounded.CardGiftcard
            else -> Icons.Rounded.Category
        }
    }
}
