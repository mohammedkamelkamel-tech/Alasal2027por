package com.example.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.local.entity.UserEntity
import com.example.domain.auth.StationRoles
import com.example.ui.theme.LocalAlasalColors

@Composable
fun AppLockScreen(
    users: List<UserEntity>,
    activeUser: UserEntity?,
    onPasswordUnlock: (username: String, password: String, onError: (String) -> Unit) -> Unit,
    onBiometricSuccess: (userId: Long, onError: (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    val activeUsers = remember(users) {
        val act = users.filter { it.isActive }
        act.ifEmpty { users }
    }

    var username by remember(activeUser?.username) {
        mutableStateOf(activeUser?.username ?: activeUsers.firstOrNull()?.username ?: "admin")
    }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeUsers) {
        if (username.isBlank() && activeUsers.isNotEmpty()) {
            username = activeUsers.first().username
        }
    }

    val selectedUser = remember(activeUsers, username) {
        activeUsers.firstOrNull { it.username.equals(username.trim(), ignoreCase = true) }
    }

    val biometricManager = remember(context) { BiometricManager.from(context) }
    val biometricCapability = remember(biometricManager) {
        runCatching {
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(alasalColors.navyBrand, alasalColors.navyBrandElevated, MaterialTheme.colorScheme.background)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, alasalColors.gold.copy(alpha = 0.65f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_alasal_logo_transparent),
                    contentDescription = stringResource(R.string.logo_content_desc),
                    modifier = Modifier.size(84.dp),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = alasalColors.gold.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = alasalColors.navyBrand,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "حماية النظام — تسجيل الدخول",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (activeUsers.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "اختيار الحساب السريع:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(activeUsers, key = { it.id }) { u ->
                                FilterChip(
                                    selected = u.username.equals(username.trim(), ignoreCase = true),
                                    onClick = {
                                        username = u.username
                                        errorText = null
                                    },
                                    label = {
                                        Text("${u.fullName} (${StationRoles.arabicTitle(u.roleCode)})")
                                    },
                                    modifier = Modifier.testTag("lock_user_chip_${u.username}")
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorText = null
                    },
                    label = { Text("اسم المستخدم") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lock_username_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorText = null
                    },
                    label = { Text("كلمة المرور") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lock_password_input")
                )

                if (errorText != null) {
                    Surface(
                        color = alasalColors.arrearsContainer,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, alasalColors.arrearsRed.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = alasalColors.arrearsRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorText!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = alasalColors.arrearsRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            errorText = "أدخل اسم المستخدم وكلمة المرور أولاً"
                        } else {
                            onPasswordUnlock(username.trim(), password) { err ->
                                errorText = err
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = alasalColors.navyBrand,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("lock_submit_button")
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("فتح التطبيق", fontWeight = FontWeight.Bold)
                }

                if (selectedUser?.biometricEnabled == true) {
                    OutlinedButton(
                        onClick = {
                            if (biometricCapability != BiometricManager.BIOMETRIC_SUCCESS) {
                                errorText = when (biometricCapability) {
                                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                                        "لم يتم تسجيل بصمة في إعدادات الجهاز بعد؛ يمكنك الدخول بكلمة المرور دائماً."
                                    else ->
                                        "المصادقة البيومترية غير متوفرة حالياً على هذا الجهاز؛ استخدم كلمة المرور."
                                }
                                return@OutlinedButton
                            }
                            val activity = context as? FragmentActivity
                            if (activity == null) {
                                errorText = "تعذر تشغيل نافذة البصمة؛ استخدم كلمة المرور."
                                return@OutlinedButton
                            }
                            val executor = ContextCompat.getMainExecutor(context)
                            val prompt = BiometricPrompt(
                                activity,
                                executor,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        onBiometricSuccess(selectedUser.id) { err ->
                                            errorText = err
                                        }
                                    }

                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        errorText = "تعذر التحقق بالبصمة ($errString) — يمكنك الدخول بكلمة المرور."
                                    }

                                    override fun onAuthenticationFailed() {
                                        errorText = "البصمة غير مطابقة، حاول مرة أخرى أو استخدم كلمة المرور."
                                    }
                                }
                            )
                            val info = BiometricPrompt.PromptInfo.Builder()
                                .setTitle("الدخول السريع — محطة العسل 2027")
                                .setSubtitle("التحقق بالبصمة لحساب ${selectedUser.fullName}")
                                .setNegativeButtonText("استخدام كلمة المرور")
                                .build()
                            prompt.authenticate(info)
                        },
                        border = BorderStroke(1.2.dp, alasalColors.gold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("lock_biometric_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "الدخول بالبصمة",
                            tint = alasalColors.gold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("الدخول بالبصمة", fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "تبقى كلمة المرور متاحة كخيار بديل إلزامي في كل الأوقات.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
