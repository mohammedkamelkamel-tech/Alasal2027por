package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.PermissionEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.auth.PermissionCatalog
import com.example.domain.auth.StationPermission
import com.example.domain.auth.StationRoles
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.LocalAlasalColors
import com.example.ui.viewmodel.FeedbackMessage

@Composable
fun AccountsAndPermissionsScreen(
    users: List<UserEntity>,
    rolePermissions: List<PermissionEntity>,
    activeUser: UserEntity?,
    effectivePermissions: Set<String>,
    feedback: FeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onNavigateBack: () -> Unit,
    onLoginWithCredentials: (String, String, () -> Unit) -> Unit,
    onCreateUser: (String, String, String, String, Boolean, Map<String, Boolean>, Boolean, () -> Unit) -> Unit,
    onUpdateUser: (Long, String, String, Boolean, Map<String, Boolean>, Boolean?, () -> Unit) -> Unit,
    onChangePassword: (Long, String, () -> Unit) -> Unit,
    onToggleUserActive: (Long, Boolean) -> Unit,
    onToggleUserBiometric: (Long, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val alasalColors = LocalAlasalColors.current
    val canManageAccounts = StationPermission.MANAGE_USERS.key in effectivePermissions

    var showLoginDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var passwordTargetUser by remember { mutableStateOf<UserEntity?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (feedback != null) {
            StatusFeedbackBanner(
                feedback = feedback,
                onDismiss = onDismissFeedback,
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("accounts_permissions_screen"),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "top_bar") {
                Row(
                    modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                        Text(
                            text = "الحسابات والصلاحيات",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = { showLoginDialog = true },
                        modifier = Modifier.height(44.dp).testTag("switch_account_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("تبديل الحساب")
                    }
                }
            }

            item(key = "active_session_card") {
                Card(
                    modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "الجلسة النشطة: ${activeUser?.fullName ?: "المدير الرئيسي"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("active_user_banner")
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = alasalColors.gold.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = StationRoles.arabicTitle(activeUser?.roleCode ?: StationRoles.ADMIN),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "الصلاحيات الفعّالة لهذه الجلسة: ${effectivePermissions.size} من ${StationPermission.entries.size} صلاحية (مفروضة برمجياً على قاعدة البيانات)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "create_action") {
                Row(
                    modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الحسابات المسجلة (${users.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        enabled = canManageAccounts,
                        modifier = Modifier.height(46.dp).testTag("create_account_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("إنشاء حساب جديد")
                    }
                }
            }

            items(users, key = { it.id }) { user ->
                val userRolePerms = rolePermissions.filter { it.roleCode == user.roleCode }
                val allowedCount = StationPermission.entries.count {
                    PermissionCatalog.isEffectivePermissionAllowed(user, userRolePerms, it.key)
                }
                Card(
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .testTag("account_card_${user.username}"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    border = BorderStroke(
                        1.dp,
                        if (user.isActive) alasalColors.cardBorder else alasalColors.arrearsRed.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (user.roleCode == StationRoles.ADMIN) Icons.Filled.AdminPanelSettings else Icons.Filled.LockPerson,
                                    contentDescription = null,
                                    tint = if (user.isActive) MaterialTheme.colorScheme.primary else alasalColors.arrearsRed
                                )
                                Column {
                                    Text(user.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "@${user.username} | ${StationRoles.arabicTitle(user.roleCode)} | الصلاحيات: $allowedCount/${StationPermission.entries.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                color = if (user.isActive) alasalColors.creditContainer else alasalColors.arrearsContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = if (user.isActive) "نشط" else "موقوف",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (user.isActive) alasalColors.creditGreen else alasalColors.arrearsRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = null,
                                    tint = if (user.biometricEnabled) alasalColors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "الدخول السريع بالبصمة (مع بقاء كلمة المرور بديلاً إلزامياً)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = user.biometricEnabled,
                                onCheckedChange = { enabled -> onToggleUserBiometric(user.id, enabled) },
                                enabled = canManageAccounts || activeUser?.id == user.id,
                                modifier = Modifier.testTag("biometric_switch_${user.username}")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { editingUser = user },
                                modifier = Modifier.weight(1f).height(44.dp).testTag("edit_account_${user.username}")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("تعديل والصلاحيات", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { passwordTargetUser = user },
                                modifier = Modifier.weight(1f).height(44.dp).testTag("change_password_${user.username}")
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("كلمة المرور", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onToggleUserActive(user.id, !user.isActive) },
                                modifier = Modifier.height(44.dp).testTag("toggle_active_${user.username}")
                            ) {
                                Text(if (user.isActive) "إيقاف" else "تفعيل", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLoginDialog) {
        LoginAccountDialog(
            users = users,
            onDismiss = { showLoginDialog = false },
            onSubmit = { username, pass ->
                onLoginWithCredentials(username, pass) { showLoginDialog = false }
            }
        )
    }

    if (showCreateDialog) {
        AccountEditorDialog(
            initialUser = null,
            rolePermissions = rolePermissions,
            onDismiss = { showCreateDialog = false },
            onSaveNew = { username, fullName, roleCode, password, isActive, perms, biometricEnabled ->
                onCreateUser(username, fullName, roleCode, password, isActive, perms, biometricEnabled) {
                    showCreateDialog = false
                }
            },
            onSaveExisting = { _, _, _, _, _, _ -> }
        )
    }

    if (editingUser != null) {
        val target = editingUser!!
        AccountEditorDialog(
            initialUser = target,
            rolePermissions = rolePermissions,
            onDismiss = { editingUser = null },
            onSaveNew = { _, _, _, _, _, _, _ -> },
            onSaveExisting = { userId, fullName, roleCode, isActive, perms, biometricEnabled ->
                onUpdateUser(userId, fullName, roleCode, isActive, perms, biometricEnabled) {
                    editingUser = null
                }
            }
        )
    }

    if (passwordTargetUser != null) {
        val target = passwordTargetUser!!
        var newPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passwordTargetUser = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 420.dp),
            title = { Text("تغيير كلمة المرور — ${target.fullName}") },
            text = {
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("كلمة المرور الجديدة (4+ خانات)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("new_password_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = { onChangePassword(target.id, newPass) { passwordTargetUser = null } },
                    modifier = Modifier.testTag("confirm_password_change_button")
                ) { Text("حفظ كلمة المرور") }
            },
            dismissButton = {
                TextButton(onClick = { passwordTargetUser = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun LoginAccountDialog(
    users: List<UserEntity>,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var username by remember { mutableStateOf(users.firstOrNull()?.username ?: "admin") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 460.dp),
        title = { Text("تسجيل الدخول / تبديل الحساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "اختر اسماً أو أدخل بيانات الحساب (الرمز الافتراضي للمدير: 2027)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    users.take(3).forEach { u ->
                        FilterChip(
                            selected = username == u.username,
                            onClick = { username = u.username },
                            label = { Text("@${u.username}") }
                        )
                    }
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("اسم المستخدم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("login_username_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("login_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(username, password) },
                modifier = Modifier.testTag("login_submit_button")
            ) { Text("دخول") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun AccountEditorDialog(
    initialUser: UserEntity?,
    rolePermissions: List<PermissionEntity>,
    onDismiss: () -> Unit,
    onSaveNew: (String, String, String, String, Boolean, Map<String, Boolean>, Boolean) -> Unit,
    onSaveExisting: (Long, String, String, Boolean, Map<String, Boolean>, Boolean) -> Unit
) {
    val isCreate = initialUser == null
    var username by remember { mutableStateOf(initialUser?.username ?: "") }
    var fullName by remember { mutableStateOf(initialUser?.fullName ?: "") }
    var roleCode by remember { mutableStateOf(initialUser?.roleCode ?: StationRoles.COLLECTOR) }
    var password by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(initialUser?.isActive ?: true) }
    var biometricEnabled by remember { mutableStateOf(initialUser?.biometricEnabled ?: false) }

    val permState = remember(initialUser?.id) {
        mutableStateMapOf<String, Boolean>().apply {
            val existingPerms = initialUser?.let {
                val rPerms = rolePermissions.filter { p -> p.roleCode == it.roleCode }
                PermissionCatalog.resolveUserPermissionMap(it, rPerms)
            } ?: PermissionCatalog.defaultPermissionsForRole(roleCode)
            putAll(existingPerms)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 580.dp)
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isCreate) "إنشاء حساب مستخدم جديد" else "تعديل الحساب وصلاحيات ${initialUser?.fullName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    if (isCreate) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("اسم المستخدم (بالإنجليزية بدون مسافات) *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("account_username_input")
                        )
                    }

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("الاسم الكامل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("account_fullname_input")
                    )

                    if (isCreate) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("كلمة المرور (4 خانات فأكثر) *") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("account_password_input")
                        )
                    }

                    Text("الدور الوظيفي:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StationRoles.allRoles.forEach { r ->
                            FilterChip(
                                selected = roleCode == r,
                                onClick = {
                                    roleCode = r
                                    val defaults = PermissionCatalog.defaultPermissionsForRole(r)
                                    permState.clear()
                                    permState.putAll(defaults)
                                },
                                label = { Text(StationRoles.arabicTitle(r)) },
                                modifier = Modifier.testTag("role_chip_$r")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الحساب نشط ويسمح بتسجيل الدخول", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الدخول السريع بالبصمة", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "تبقى كلمة المرور خياراً بديلاً إلزامياً في كل الأوقات",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = biometricEnabled, onCheckedChange = { biometricEnabled = it })
                    }

                    HorizontalDivider()
                    Text("مصفوفة الصلاحيات التفصيلية:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    StationPermission.entries.forEach { perm ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(perm.titleAr, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = perm.groupAr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = permState[perm.key] ?: false,
                                onCheckedChange = { checked -> permState[perm.key] = checked },
                                modifier = Modifier.testTag("perm_checkbox_${perm.key}")
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("إلغاء") }
                    Button(
                        onClick = {
                            if (isCreate) {
                                onSaveNew(username, fullName, roleCode, password, isActive, permState.toMap(), biometricEnabled)
                            } else {
                                onSaveExisting(initialUser!!.id, fullName, roleCode, isActive, permState.toMap(), biometricEnabled)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("save_account_button")
                    ) { Text("حفظ الحساب") }
                }
            }
        }
    }
}
