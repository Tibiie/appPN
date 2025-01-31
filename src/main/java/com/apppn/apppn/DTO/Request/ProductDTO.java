package com.apppn.apppn.DTO.Request;

import org.springframework.web.multipart.MultipartFile;

public class ProductDTO {

    private String producto;
    private String descripcion;
    private MultipartFile imagen;
    private Long clasificacionProducto;

    public ProductDTO() {
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Long getClasificacionProducto() {
        return clasificacionProducto;
    }

    public void setClasificacionProducto(Long clasificacionProducto) {
        this.clasificacionProducto = clasificacionProducto;
    }

    public MultipartFile getImagen() {
        return imagen;
    }

    public void setImagen(MultipartFile imagen) {
        this.imagen = imagen;
    }

}
